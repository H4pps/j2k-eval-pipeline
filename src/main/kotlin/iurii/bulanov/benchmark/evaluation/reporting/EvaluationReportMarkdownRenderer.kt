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
            appendResultInterpretation(result)
            appendPaths(result)
            appendConversion(result.conversion)
            appendCheckout(result.checkout)
            appendFileCoverage(result.fileCoverage)
            appendStructure(result.structure)
            appendContent(result.content)
            appendNullability(result.nullability)
            appendQuality(result.quality)
            appendNotableFailures(result)
            appendWarnings(result)
        }

    private fun StringBuilder.appendResultInterpretation(result: EvaluationResult) {
        appendLine()
        appendLine("## Result Interpretation")
        appendLine()
        appendLine("- Verdict: ${resultVerdict(result)}")
        appendLine(
            "- Coverage: `${result.fileCoverage.matchedKotlinFileCount}` of `${result.fileCoverage.javaFileCount}` " +
                "configured Java inputs have matching generated Kotlin files " +
                "(`${formatPercent(result.fileCoverage.coveragePercent)}%`).",
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

    private fun StringBuilder.appendConversion(conversion: ConversionEvaluation) {
        appendLine()
        appendLine("## Conversion Execution")
        appendLine()
        appendLine("- Available: `${conversion.available}`")
        appendLine("- Status: `${conversion.status}`")
        appendLine("- Source Java files: `${conversion.sourceJavaFileCount ?: "unknown"}`")
        appendLine("- Generated Kotlin files: `${conversion.generatedKotlinFileCount ?: "unknown"}`")
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

    private fun StringBuilder.appendFileCoverage(fileCoverage: FileCoverageMetrics) {
        appendLine()
        appendLine("## File Coverage")
        appendLine()
        appendLine("- Java files discovered: `${fileCoverage.javaFileCount}`")
        appendLine("- Kotlin files discovered: `${fileCoverage.kotlinFileCount}`")
        appendLine("- Matched Kotlin files: `${fileCoverage.matchedKotlinFileCount}`")
        appendLine("- Coverage: `${formatPercent(fileCoverage.coveragePercent)}%`")
        appendLine("- Missing Kotlin files: `${fileCoverage.missingKotlinFiles.size}`")
        appendLine("- Unexpected Kotlin files: `${fileCoverage.unexpectedKotlinFiles.size}`")
        appendLine("- Empty generated files: `${fileCoverage.emptyGeneratedFiles.size}`")
        appendLine("- Package preservation: `${formatPercent(fileCoverage.packagePreservationPercent)}%`")
    }

    private fun StringBuilder.appendStructure(structure: StructuralMetrics) {
        appendLine()
        appendLine("## Structural Preservation")
        appendLine()
        appendLine(
            "Generated Kotlin is compared with " +
                "the original Java source using deterministic structural heuristics. Name-level structural diffs are " +
                "calculated only for matched Java/Kotlin files; missing generated files are reported under File Coverage.",
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
        appendLine()
        appendLine("## Content Preservation")
        appendLine()
        appendLine(
            "Generated Kotlin function bodies are compared with original Java method bodies using parser-backed " +
                "body-shape heuristics.",
        )
        appendLine()
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
        appendLine(
            "- Control-flow fidelity score: `${formatScore(content.controlFlowFidelityScore)}` " +
                "(returns `${content.kotlinReturnCount}/${content.javaReturnCount}`, " +
                "branches `${content.kotlinBranchCount}/${content.javaBranchCount}`, " +
                "throws `${content.kotlinThrowCount}/${content.javaThrowCount}`, " +
                "tries `${content.kotlinTryCount}/${content.javaTryCount}`)",
        )
        appendLine(
            "- Content-shape preservation rate: `${formatScore(content.contentShapePreservationRate)}` " +
                "(`${content.contentShapePreservedFileCount}/${content.matchedFileCount}` matched files preserved; " +
                "mismatches `${content.contentShapeMismatchFileCount}`)",
        )
        appendLine(
            "- Return density: Java `${formatScore(content.javaReturnDensity)}`, " +
                "Kotlin `${formatScore(content.kotlinReturnDensity)}`, " +
                "preservation `${formatScore(content.returnStatementDensityPreservation)}` " +
                "(returns per function `${content.javaReturnCount}/${content.javaFunctionDeclarationCount}` vs " +
                "`${content.kotlinReturnCount}/${content.kotlinFunctionDeclarationCount}`)",
        )
        appendLine(
            "- Branch complexity index: Java `${formatScore(content.javaBranchComplexityIndex)}`, " +
                "Kotlin `${formatScore(content.kotlinBranchComplexityIndex)}`, " +
                "preservation `${formatScore(content.branchComplexityIndexPreservation)}` " +
                "(branches+loops+tries per function " +
                "`${content.javaBranchCount + content.javaLoopCount + content.javaTryCount}/${content.javaFunctionDeclarationCount}` vs " +
                "`${content.kotlinBranchCount + content.kotlinLoopCount + content.kotlinTryCount}/${content.kotlinFunctionDeclarationCount}`)",
        )
        appendNameList("Java method bodies missing in Kotlin", content.missingKotlinBodies)
        appendNameList("Files with content-shape mismatches", content.contentShapeMismatchFiles)
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
        appendLine("- Nullability inference accuracy: `${formatScore(nullability.nullabilityInferenceAccuracy)}`")
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

    private companion object {
        private const val COMPLETE_COVERAGE = 100.0
        private const val STRUCTURAL_NAME_DISPLAY_LIMIT = 50
    }
}
