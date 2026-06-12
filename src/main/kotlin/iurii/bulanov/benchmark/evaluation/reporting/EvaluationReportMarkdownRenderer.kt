package iurii.bulanov.benchmark.evaluation.reporting

import iurii.bulanov.benchmark.evaluation.CheckoutEvaluation
import iurii.bulanov.benchmark.evaluation.ContentMetrics
import iurii.bulanov.benchmark.evaluation.ConversionEvaluation
import iurii.bulanov.benchmark.evaluation.EvaluationResult
import iurii.bulanov.benchmark.evaluation.EvaluationWarning
import iurii.bulanov.benchmark.evaluation.FileCoverageMetrics
import iurii.bulanov.benchmark.evaluation.NullabilityMetrics
import iurii.bulanov.benchmark.evaluation.QualityMetrics
import iurii.bulanov.benchmark.evaluation.StructuralMetrics
import iurii.bulanov.benchmark.evaluation.StructuralNameDiff
import java.util.Locale

/**
 * Renders the human-readable evaluator summary report body.
 */
internal class EvaluationReportMarkdownRenderer : EvaluationReportRenderer {
    /**
     * Renders [result] as deterministic Markdown.
     */
    override fun render(result: EvaluationResult): String =
        buildString {
            appendLine("# J2K Evaluation Summary")
            appendSummary(result)
            appendPaths(result)
            appendConversionCoverage(result.conversion, result.fileCoverage)
            appendCheckout(result.checkout)
            appendStructure(result.structure)
            appendContent(result.content)
            appendNullability(result.nullability)
            appendQuality(result.quality)
            appendNotableFailures(result)
            appendWarnings(result)
        }

    private fun StringBuilder.appendSummary(result: EvaluationResult) {
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Verdict: ${resultVerdict(result)}")
        appendLine("- Converter status: `${result.conversion.status}`")
        appendLine(
            "- Coverage: `${result.fileCoverage.matchedKotlinFileCount}` of `${result.fileCoverage.javaFileCount}` " +
                "Java inputs have matching generated Kotlin files " +
                "(`${formatPercent(result.fileCoverage.coveragePercent)}%`).",
        )
        appendLine(
            "- Converter issues: `${result.conversion.warningCount}` warnings, `${result.conversion.errorCount}` errors.",
        )
        appendLine(
            "- Structural comparison: Java declarations/functions are compared with generated Kotlin " +
                "declarations/functions as deterministic heuristics, not as a compiler proof.",
        )
        appendLine(
            "- Quality review: `${result.quality.findings.size}` Kotlin quality warnings were produced for " +
                "manual review.",
        )
        appendLine(
            "- Content/nullability review: `${result.content.findings.size + result.nullability.findings.size}` " +
                "parser-backed content or nullability warnings were produced.",
        )
    }

    private fun StringBuilder.appendPaths(result: EvaluationResult) {
        appendLine()
        appendLine("## Paths")
        appendLine()
        appendLine("- Converter kind: `${result.kind.id}`")
        appendLine("- Checkout directory: `${result.checkoutDirectory}`")
        appendLine("- Generated Kotlin directory: `${result.generatedKotlinDirectory}`")
        appendLine("- Conversion report: `${result.conversionReportPath}`")
        appendLine("- Checkout report: `${result.checkoutReportPath}`")
        appendLine("- Report directory: `${result.reportDirectory}`")
    }

    private fun StringBuilder.appendConversionCoverage(
        conversion: ConversionEvaluation,
        fileCoverage: FileCoverageMetrics,
    ) {
        appendLine()
        appendLine("## Conversion Coverage")
        appendLine()
        appendLine("- Conversion metadata available: `${conversion.available}`")
        appendLine("- Converter status: `${conversion.status}`")
        appendLine("- Source Java files: `${conversion.sourceJavaFileCount ?: "unknown"}`")
        appendLine("- Generated Kotlin files: `${conversion.generatedKotlinFileCount ?: "unknown"}`")
        appendLine("- Java files discovered: `${fileCoverage.javaFileCount}`")
        appendLine("- Kotlin files discovered: `${fileCoverage.kotlinFileCount}`")
        appendLine("- Matched Kotlin files: `${fileCoverage.matchedKotlinFileCount}`")
        appendLine("- Coverage: `${formatPercent(fileCoverage.coveragePercent)}%`")
        appendLine("- Missing Kotlin files: `${fileCoverage.missingKotlinFiles.size}`")
        appendLine("- Unexpected Kotlin files: `${fileCoverage.unexpectedKotlinFiles.size}`")
        appendLine("- Empty generated files: `${fileCoverage.emptyGeneratedFiles.size}`")
        appendLine("- Package preservation: `${formatPercent(fileCoverage.packagePreservationPercent)}%`")
        appendLine("- Converter warnings: `${conversion.warningCount}`")
        appendLine("- Converter errors: `${conversion.errorCount}`")
    }

    private fun StringBuilder.appendCheckout(checkout: CheckoutEvaluation) {
        appendLine()
        appendLine("## Original Build")
        appendLine()
        appendLine("- Checkout report available: `${checkout.available}`")
        appendLine("- Build status: `${checkout.buildStatus}`")
        appendLine("- Run build: `${checkout.runBuild ?: "unknown"}`")
    }

    private fun StringBuilder.appendStructure(structure: StructuralMetrics) {
        appendLine()
        appendLine("## Structural Preservation")
        appendLine()
        appendLine(
            "Generated Kotlin is compared with " +
                "the original Java source using deterministic structural heuristics. Name-level structural diffs are " +
                "calculated only for matched Java/Kotlin files; missing generated files are reported under Conversion Coverage.",
        )
        appendLine()
        appendLine("- Java declarations: `${structure.javaTopLevelDeclarationCount}`")
        appendLine("- Kotlin declarations: `${structure.kotlinTopLevelDeclarationCount}`")
        appendLine("- Java class-like declarations: `${structure.javaClassLikeCount}`")
        appendLine("- Kotlin class-like declarations: `${structure.kotlinClassLikeCount}`")
        appendLine("- Java interfaces: `${structure.javaInterfaceCount}`")
        appendLine("- Kotlin interfaces: `${structure.kotlinInterfaceCount}`")
        appendLine("- Java enums: `${structure.javaEnumCount}`")
        appendLine("- Kotlin enums: `${structure.kotlinEnumCount}`")
        appendLine("- Java methods: `${structure.javaMethodCount}`")
        appendLine("- Kotlin functions: `${structure.kotlinFunctionCount}`")
        appendLine("- Public API name overlap: `${structure.publicApiNameOverlapCount}`")
        appendLine("- Java API names missing in Kotlin: `${structure.missingPublicApiNames.size}`")
        appendLine("- Kotlin-only API names: `${structure.kotlinOnlyPublicApiNames.size}`")
        appendLine()
        appendLine("### Structural Name Differences")
        appendLine()
        appendLine("These name lists are parser-backed; Java getters/setters may become Kotlin properties.")
        appendLine(
            "Markdown caps long lists at `$STRUCTURAL_NAME_DISPLAY_LIMIT` entries; full lists are in `evaluation.json` under `structure.name_diffs`.",
        )
        appendNameList("Java classes/records converted to Kotlin objects", structure.nameDiffs.classLikeToObjectNames)
        appendNameDiff(
            "Classes and records",
            "Java classes/records missing in Kotlin",
            "Kotlin classes not present in Java",
            structure.nameDiffs.classLike,
        )
        appendNameDiff(
            "Interfaces",
            "Java interfaces missing in Kotlin",
            "Kotlin interfaces not present in Java",
            structure.nameDiffs.interfaces,
        )
        appendNameDiff("Enums", "Java enums missing in Kotlin", "Kotlin enums not present in Java", structure.nameDiffs.enums)
        appendNameList("Kotlin objects not present in Java", structure.nameDiffs.objects.kotlinOnly)
        appendNameList("Java bean accessors backed by Kotlin properties", structure.nameDiffs.javaBeanAccessorNames)
        appendNameDiff(
            "Methods and functions",
            "Java methods missing as Kotlin functions",
            "Kotlin functions not present as Java methods",
            structure.nameDiffs.functions,
        )
    }

    private fun StringBuilder.appendQuality(quality: QualityMetrics) {
        appendLine()
        appendLine("## Kotlin Quality Warnings")
        appendLine()
        appendLine("- `TODO()` calls: `${quality.todoCount}`")
        appendLine("- `!!` assertions: `${quality.notNullAssertionCount}`")
        appendLine("- `!!` inside calls: `${quality.notNullAssertionInCallCount}`")
        appendLine("- `Any?` types: `${quality.anyNullableCount}`")
        appendLine("- Unresolved-looking imports: `${quality.unresolvedImportCount}`")
        appendLine("- Java interop leftovers: `${quality.javaInteropReferenceCount}`")
        appendLine("- Getter/setter leftovers: `${quality.getterSetterCallCount}`")
        appendLine("- Nullable boolean comparisons: `${quality.nullableBooleanComparisonCount}`")
        appendLine("- Eager property initializers: `${quality.eagerPropertyInitializationCount}`")
    }

    private fun StringBuilder.appendContent(content: ContentMetrics) {
        appendContentHeader()
        appendContentCounts(content)
        appendContentPreservation(content)
        appendNameList("Java method bodies missing in Kotlin", content.missingKotlinBodies)
        appendNameList("Files with content-shape mismatches", content.contentShapeMismatchFiles)
    }

    private fun StringBuilder.appendContentHeader() {
        appendLine()
        appendLine("## Content Preservation")
        appendLine()
        appendLine(
            "Generated Kotlin function bodies are compared with original Java method bodies using parser-backed " +
                "body-shape heuristics.",
        )
        appendLine()
    }

    private fun StringBuilder.appendContentCounts(content: ContentMetrics) {
        appendLine("- Matched files analyzed: `${content.matchedFileCount}`")
        appendLine("- Java non-empty methods: `${content.javaNonEmptyMethodCount}`")
        appendLine("- Kotlin non-empty functions: `${content.kotlinNonEmptyFunctionCount}`")
        appendLine("- Java empty methods: `${content.javaEmptyMethodCount}`")
        appendLine("- Kotlin empty functions: `${content.kotlinEmptyFunctionCount}`")
        appendLine("- Missing Kotlin bodies: `${content.missingKotlinBodies.size}`")
        appendLine("- Content-shape mismatch files: `${content.contentShapeMismatchFiles.size}`")
        appendLine("- Return statements: Java `${content.javaReturnCount}`, Kotlin `${content.kotlinReturnCount}`")
        appendLine("- Branches: Java `${content.javaBranchCount}`, Kotlin `${content.kotlinBranchCount}`")
        appendLine("- Loops: Java `${content.javaLoopCount}`, Kotlin `${content.kotlinLoopCount}`")
        appendLine("- Throws: Java `${content.javaThrowCount}`, Kotlin `${content.kotlinThrowCount}`")
        appendLine("- Try blocks: Java `${content.javaTryCount}`, Kotlin `${content.kotlinTryCount}`")
        appendLine(
            "- Function declarations: Java `${content.javaFunctionDeclarationCount}`, Kotlin `${content.kotlinFunctionDeclarationCount}`",
        )
    }

    private fun StringBuilder.appendContentPreservation(content: ContentMetrics) {
        appendContentShapePreservation(content)
        appendControlFlowPreservation(content)
        appendDensityPreservation(content)
    }

    private fun StringBuilder.appendContentShapePreservation(content: ContentMetrics) {
        appendLine(
            "- Content shape preserved files: " +
                countPreservation(
                    numerator = content.contentShapePreservedFileCount,
                    denominator = content.matchedFileCount,
                    score = content.contentShapePreservationRate,
                ) +
                " (mismatches `${content.contentShapeMismatchFileCount}`)",
        )
    }

    private fun StringBuilder.appendControlFlowPreservation(content: ContentMetrics) {
        appendLine(
            "- Returns preserved: " +
                countPreservation(
                    numerator = content.kotlinReturnCount,
                    denominator = content.javaReturnCount,
                    score = content.returnPreservationRatio,
                ),
        )
        appendLine(
            "- Branches preserved: " +
                countPreservation(
                    numerator = content.kotlinBranchCount,
                    denominator = content.javaBranchCount,
                    score = content.branchPreservationRatio,
                ),
        )
        appendLine(
            "- Throws preserved: " +
                countPreservation(
                    numerator = content.kotlinThrowCount,
                    denominator = content.javaThrowCount,
                    score = content.throwPreservationRatio,
                ),
        )
        appendLine(
            "- Try blocks preserved: " +
                countPreservation(
                    numerator = content.kotlinTryCount,
                    denominator = content.javaTryCount,
                    score = content.tryPreservationRatio,
                ),
        )
        appendLine(
            "- Control-flow fidelity score: " +
                "returns ${content.kotlinReturnCount}/${content.javaReturnCount}, " +
                "branches ${content.kotlinBranchCount}/${content.javaBranchCount}, " +
                "throws ${content.kotlinThrowCount}/${content.javaThrowCount}, " +
                "tries ${content.kotlinTryCount}/${content.javaTryCount} " +
                "(${formatScorePercent(content.controlFlowFidelityScore)})",
        )
    }

    private fun StringBuilder.appendDensityPreservation(content: ContentMetrics) {
        appendLine(
            "- Java return rate: " +
                rate(content.javaReturnCount, content.javaFunctionDeclarationCount),
        )
        appendLine(
            "- Kotlin return rate: " +
                rate(content.kotlinReturnCount, content.kotlinFunctionDeclarationCount),
        )
        appendLine(
            "- Return rate preserved: " +
                ratePreservation(
                    javaNumerator = content.javaReturnCount,
                    javaDenominator = content.javaFunctionDeclarationCount,
                    kotlinNumerator = content.kotlinReturnCount,
                    kotlinDenominator = content.kotlinFunctionDeclarationCount,
                    score = content.returnStatementDensityPreservation,
                ),
        )
        val javaControlFlowCount = content.javaBranchCount + content.javaLoopCount + content.javaTryCount
        val kotlinControlFlowCount = content.kotlinBranchCount + content.kotlinLoopCount + content.kotlinTryCount
        appendLine(
            "- Java control-flow rate: " +
                rate(javaControlFlowCount, content.javaFunctionDeclarationCount),
        )
        appendLine(
            "- Kotlin control-flow rate: " +
                rate(kotlinControlFlowCount, content.kotlinFunctionDeclarationCount),
        )
        appendLine(
            "- Control-flow rate preserved: " +
                controlFlowRatePreservation(
                    javaBranchCount = content.javaBranchCount,
                    javaLoopCount = content.javaLoopCount,
                    javaTryCount = content.javaTryCount,
                    javaFunctionCount = content.javaFunctionDeclarationCount,
                    kotlinBranchCount = content.kotlinBranchCount,
                    kotlinLoopCount = content.kotlinLoopCount,
                    kotlinTryCount = content.kotlinTryCount,
                    kotlinFunctionCount = content.kotlinFunctionDeclarationCount,
                    score = content.branchComplexityIndexPreservation,
                ),
        )
    }

    private fun StringBuilder.appendNullability(nullability: NullabilityMetrics) {
        appendLine()
        appendLine("## Nullability Signals")
        appendLine()
        appendLine(
            "Java nullability annotations are compared with generated Kotlin nullable types by declaration name.",
        )
        appendLine()
        appendLine("- Java nullable annotations: `${nullability.javaNullableAnnotationCount}`")
        appendLine("- Java not-null annotations: `${nullability.javaNotNullAnnotationCount}`")
        appendLine("- Kotlin nullable types: `${nullability.kotlinNullableTypeCount}`")
        appendLine("- Contradictory nullability patterns: `${nullability.contradictoryNullabilityPatterns}`")
        appendLine(
            "- Total nullability operations: `${nullability.totalNullabilityOperationCount}` " +
                "(null comparisons `${nullability.nullComparisonCount}`, casts `${nullability.nullabilityCastCount}`, " +
                "safe calls `${nullability.safeCallCount}`)",
        )
        appendLine("- Nullability inference accuracy: ${nullabilityInferenceAccuracy(nullability)}")
        appendLine("- Nullable annotations not preserved: `${nullability.nullableAnnotationsNotPreserved.size}`")
        appendLine("- Not-null annotations converted to nullable: `${nullability.notNullAnnotationsBecameNullable.size}`")
        appendNameList("Nullable Java declarations not preserved as nullable Kotlin", nullability.nullableAnnotationsNotPreserved)
        appendNameList("Not-null Java declarations converted to nullable Kotlin", nullability.notNullAnnotationsBecameNullable)
    }

    private fun StringBuilder.appendNotableFailures(result: EvaluationResult) {
        appendLine()
        appendLine("## Notable Failures")
        appendLine()
        if (!result.hasNotableFailures()) {
            appendLine("- None")
            return
        }

        appendConversionFailures(result.conversion)
        appendFileCoverageFailures(result.fileCoverage)
        appendReviewWarningSummaries(result)
    }

    private fun EvaluationResult.hasNotableFailures(): Boolean =
        conversion.errors.isNotEmpty() ||
            fileCoverage.missingKotlinFiles.isNotEmpty() ||
            fileCoverage.packageMismatchFiles.isNotEmpty() ||
            content.findings.isNotEmpty() ||
            nullability.findings.isNotEmpty() ||
            quality.findings.isNotEmpty()

    private fun StringBuilder.appendConversionFailures(conversion: ConversionEvaluation) {
        if (conversion.errors.isNotEmpty()) {
            appendLine("- Converter errors: `${conversion.errors.size}`")
            conversion.errors.forEach { error -> appendLine("  - `$error`") }
        }
    }

    private fun StringBuilder.appendFileCoverageFailures(fileCoverage: FileCoverageMetrics) {
        if (fileCoverage.missingKotlinFiles.isNotEmpty()) {
            appendLine("- Missing generated Kotlin files: `${fileCoverage.missingKotlinFiles.size}`")
            fileCoverage.missingKotlinFiles.forEach { missingFile -> appendLine("  - `$missingFile`") }
        }
        if (fileCoverage.packageMismatchFiles.isNotEmpty()) {
            appendLine("- Package mismatches: `${fileCoverage.packageMismatchFiles.size}`")
            fileCoverage.packageMismatchFiles.forEach { mismatch -> appendLine("  - `$mismatch`") }
        }
    }

    private fun StringBuilder.appendReviewWarningSummaries(result: EvaluationResult) {
        appendReviewWarningSummary(
            title = "Content preservation",
            count = result.content.findings.size,
            suffix = "body-shape review risks",
        )
        appendReviewWarningSummary(
            title = "Nullability preservation",
            count = result.nullability.findings.size,
            suffix = "annotation/type review risks",
        )
        appendReviewWarningSummary(
            title = "Kotlin quality",
            count = result.quality.findings.size,
            suffix = "review risks",
        )
    }

    private fun StringBuilder.appendReviewWarningSummary(
        title: String,
        count: Int,
        suffix: String,
    ) {
        if (count > 0) {
            appendLine("- $title review warnings: `$count` ($suffix, not conversion execution failures)")
        }
    }

    private fun StringBuilder.appendWarnings(result: EvaluationResult) {
        appendLine()
        appendLine("## Warnings")
        appendLine()
        appendLine("- Warnings: `${result.warnings.size}`")
        appendLine("- Status: `${result.status.name.lowercase()}`")
        appendLine()
        if (result.warnings.isEmpty()) {
            appendLine("- None")
        } else {
            result.warnings.forEach { warning -> appendWarning(warning) }
        }
    }

    private fun StringBuilder.appendWarning(warning: EvaluationWarning) {
        val pathSuffix = warning.path?.let { " `$it`" }.orEmpty()
        val countSuffix = warning.count?.let { " count=`$it`" }.orEmpty()
        appendLine("- `${warning.code}`$pathSuffix$countSuffix: ${warning.message}")
    }

    private fun StringBuilder.appendNameList(
        title: String,
        names: List<String>,
    ) {
        appendLine()
        if (names.isEmpty()) {
            appendLine("- $title: none")
            return
        }

        val displayedNames = names.take(STRUCTURAL_NAME_DISPLAY_LIMIT)
        val suffix =
            if (names.size > STRUCTURAL_NAME_DISPLAY_LIMIT) {
                "first `${displayedNames.size}` of `${names.size}`"
            } else {
                "all `${names.size}`"
            }
        appendLine("- $title ($suffix):")
        displayedNames.forEach { name -> appendLine("  - `$name`") }
    }

    private fun StringBuilder.appendNameDiff(
        title: String,
        missingInKotlinTitle: String,
        kotlinOnlyTitle: String,
        diff: StructuralNameDiff,
    ) {
        appendLine()
        appendLine("#### $title")
        appendNameList(missingInKotlinTitle, diff.missingInKotlin)
        appendNameList(kotlinOnlyTitle, diff.kotlinOnly)
    }

    private fun resultVerdict(result: EvaluationResult): String =
        when {
            !result.conversion.available ->
                "The evaluator ran, but conversion metadata is missing, so this artifact is incomplete."
            result.conversion.errors.isNotEmpty() || result.fileCoverage.missingKotlinFiles.isNotEmpty() ->
                "Static J2K produced a partial conversion with concrete failures that need manual review."
            result.fileCoverage.coveragePercent == COMPLETE_COVERAGE ->
                "Static J2K generated Kotlin for every configured Java input; remaining warnings are quality-review signals."
            else ->
                "Static J2K produced generated Kotlin, but file coverage is incomplete and should be reviewed."
        }

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun formatScore(value: Double): String = String.format(Locale.US, "%.3f", value)

    private fun formatScorePercent(value: Double): String = String.format(Locale.US, "%.1f%%", value * PERCENT_SCALE)

    private fun countPreservation(
        numerator: Int,
        denominator: Int,
        score: Double,
    ): String = "$numerator/$denominator (${formatScorePercent(score)}${cappedSuffix(numerator, denominator, score)})"

    private fun nullabilityInferenceAccuracy(nullability: NullabilityMetrics): String {
        val nonContradictoryOperations =
            (nullability.totalNullabilityOperationCount - nullability.contradictoryNullabilityPatterns).coerceAtLeast(0)
        return "$nonContradictoryOperations/${nullability.totalNullabilityOperationCount} " +
            "(${formatScorePercent(nullability.nullabilityInferenceAccuracy)})"
    }

    private fun ratePreservation(
        javaNumerator: Int,
        javaDenominator: Int,
        kotlinNumerator: Int,
        kotlinDenominator: Int,
        score: Double,
    ): String {
        val javaRate = density(javaNumerator, javaDenominator)
        val kotlinRate = density(kotlinNumerator, kotlinDenominator)
        return "${formatRate(kotlinRate)}/${formatRate(javaRate)} " +
            "(${formatScorePercent(score)}${cappedDensitySuffix(javaRate, kotlinRate, score)})"
    }

    private fun controlFlowRatePreservation(
        javaBranchCount: Int,
        javaLoopCount: Int,
        javaTryCount: Int,
        javaFunctionCount: Int,
        kotlinBranchCount: Int,
        kotlinLoopCount: Int,
        kotlinTryCount: Int,
        kotlinFunctionCount: Int,
        score: Double,
    ): String {
        val javaComplexityCount = javaBranchCount + javaLoopCount + javaTryCount
        val kotlinComplexityCount = kotlinBranchCount + kotlinLoopCount + kotlinTryCount
        val javaRate = density(javaComplexityCount, javaFunctionCount)
        val kotlinRate = density(kotlinComplexityCount, kotlinFunctionCount)
        return "${formatRate(kotlinRate)}/${formatRate(javaRate)} " +
            "(${formatScorePercent(score)}${cappedDensitySuffix(javaRate, kotlinRate, score)})"
    }

    private fun cappedSuffix(
        numerator: Int,
        denominator: Int,
        score: Double,
    ): String =
        if (score >= PERFECT_PRESERVATION && numeratorExceedsDenominator(numerator, denominator)) {
            ", capped"
        } else {
            ""
        }

    private fun cappedDensitySuffix(
        javaDensity: Double,
        kotlinDensity: Double,
        score: Double,
    ): String =
        if (score >= PERFECT_PRESERVATION && kotlinDensityExceedsJavaDensity(kotlinDensity, javaDensity)) {
            ", capped"
        } else {
            ""
        }

    private fun numeratorExceedsDenominator(
        numerator: Int,
        denominator: Int,
    ): Boolean =
        if (denominator == 0) {
            numerator > 0
        } else {
            numerator.toDouble() / denominator.toDouble() > PERFECT_PRESERVATION
        }

    private fun kotlinDensityExceedsJavaDensity(
        kotlinDensity: Double,
        javaDensity: Double,
    ): Boolean =
        if (javaDensity == 0.0) {
            kotlinDensity > 0.0
        } else {
            kotlinDensity / javaDensity > PERFECT_PRESERVATION
        }

    private fun density(
        numerator: Int,
        denominator: Int,
    ): Double =
        if (denominator == 0) {
            0.0
        } else {
            numerator.toDouble() / denominator.toDouble()
        }

    private fun rate(
        numerator: Int,
        denominator: Int,
    ): String = "$numerator/$denominator = ${formatRate(density(numerator, denominator))}"

    private fun formatRate(value: Double): String = String.format(Locale.US, "%.3f", value)

    private companion object {
        private const val COMPLETE_COVERAGE = 100.0
        private const val PERCENT_SCALE = 100.0
        private const val PERFECT_PRESERVATION = 1.0
        private const val STRUCTURAL_NAME_DISPLAY_LIMIT = 50
    }
}
