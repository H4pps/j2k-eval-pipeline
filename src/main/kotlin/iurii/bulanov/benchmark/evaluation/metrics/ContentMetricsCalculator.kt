package iurii.bulanov.benchmark.evaluation.metrics

import iurii.bulanov.benchmark.evaluation.ContentMetrics
import iurii.bulanov.benchmark.evaluation.EvaluationWarning
import iurii.bulanov.benchmark.evaluation.SourceContentProfile

/**
 * Calculates parser-backed body and control-flow preservation metrics across matched files.
 */
internal class ContentMetricsCalculator(
    private val javaBeanPropertyMatcher: JavaBeanPropertyMatcher = JavaBeanPropertyMatcher(),
) : MatchedSourceMetricsCalculator<ContentMetrics> {
    /**
     * Calculates content preservation, density, and control-flow metrics.
     */
    override fun calculate(pairs: List<MatchedSourceStructure>): ContentMetrics {
        val counts = contentCounts(pairs)
        val missingBodies = missingBodies(pairs)
        val shapeMismatchFiles = shapeMismatchFiles(pairs)
        val scores = contentScores(counts, pairs.size, shapeMismatchFiles.size)

        return ContentMetrics(
            matchedFileCount = pairs.size,
            javaNonEmptyMethodCount = pairs.sumOf { it.java.content.nonEmptyFunctionNames.size },
            kotlinNonEmptyFunctionCount = pairs.sumOf { it.kotlin.content.nonEmptyFunctionNames.size },
            javaEmptyMethodCount = pairs.sumOf { it.java.content.emptyFunctionNames.size },
            kotlinEmptyFunctionCount = pairs.sumOf { it.kotlin.content.emptyFunctionNames.size },
            missingKotlinBodies = missingBodies,
            contentShapeMismatchFiles = shapeMismatchFiles,
            javaReturnCount = counts.javaReturnCount,
            kotlinReturnCount = counts.kotlinReturnCount,
            javaBranchCount = counts.javaBranchCount,
            kotlinBranchCount = counts.kotlinBranchCount,
            javaLoopCount = counts.javaLoopCount,
            kotlinLoopCount = counts.kotlinLoopCount,
            javaThrowCount = counts.javaThrowCount,
            kotlinThrowCount = counts.kotlinThrowCount,
            javaTryCount = counts.javaTryCount,
            kotlinTryCount = counts.kotlinTryCount,
            javaFunctionDeclarationCount = counts.javaFunctionDeclarationCount,
            kotlinFunctionDeclarationCount = counts.kotlinFunctionDeclarationCount,
            contentShapePreservedFileCount = pairs.size - shapeMismatchFiles.size,
            contentShapeMismatchFileCount = shapeMismatchFiles.size,
            returnPreservationRatio = scores.returnPreservationRatio,
            branchPreservationRatio = scores.branchPreservationRatio,
            throwPreservationRatio = scores.throwPreservationRatio,
            tryPreservationRatio = scores.tryPreservationRatio,
            controlFlowFidelityScore = scores.controlFlowFidelityScore,
            contentShapePreservationRate = scores.contentShapePreservationRate,
            javaReturnDensity = scores.javaReturnDensity,
            kotlinReturnDensity = scores.kotlinReturnDensity,
            returnStatementDensityPreservation = scores.returnStatementDensityPreservation,
            javaBranchComplexityIndex = scores.javaBranchComplexityIndex,
            kotlinBranchComplexityIndex = scores.kotlinBranchComplexityIndex,
            branchComplexityIndexPreservation = scores.branchComplexityIndexPreservation,
            findings = contentFindings(missingBodies, shapeMismatchFiles),
        )
    }

    private fun missingBodies(pairs: List<MatchedSourceStructure>): List<String> =
        pairs
            .flatMap { pair ->
                val propertyBackedAccessorNames = javaBeanPropertyMatcher.propertyBackedAccessorNames(pair.java, pair.kotlin)
                pair.java.content.nonEmptyFunctionNames
                    .filterNot { name -> name in pair.kotlin.content.nonEmptyFunctionNames || name in propertyBackedAccessorNames }
                    .map { name -> "${pair.path}$MEMBER_PATH_SEPARATOR$name" }
            }.sorted()

    private fun shapeMismatchFiles(pairs: List<MatchedSourceStructure>): List<String> =
        pairs
            .filter { pair ->
                val propertyBackedAccessorNames = javaBeanPropertyMatcher.propertyBackedAccessorNames(pair.java, pair.kotlin)
                pair.java.content.hasNonPropertyBackedExecutableMethods(propertyBackedAccessorNames) &&
                    pair.java.content.hasShapeMissingFrom(pair.kotlin.content)
            }.map { it.path }
            .sorted()

    private fun contentCounts(pairs: List<MatchedSourceStructure>): ContentCounts =
        ContentCounts(
            javaReturnCount = pairs.sumOf { it.java.content.returnCount },
            kotlinReturnCount = pairs.sumOf { it.kotlin.content.returnCount },
            javaBranchCount = pairs.sumOf { it.java.content.branchCount },
            kotlinBranchCount = pairs.sumOf { it.kotlin.content.branchCount },
            javaLoopCount = pairs.sumOf { it.java.content.loopCount },
            kotlinLoopCount = pairs.sumOf { it.kotlin.content.loopCount },
            javaThrowCount = pairs.sumOf { it.java.content.throwCount },
            kotlinThrowCount = pairs.sumOf { it.kotlin.content.throwCount },
            javaTryCount = pairs.sumOf { it.java.content.tryCount },
            kotlinTryCount = pairs.sumOf { it.kotlin.content.tryCount },
            javaFunctionDeclarationCount = pairs.sumOf { it.java.content.functionDeclarationCount },
            kotlinFunctionDeclarationCount = pairs.sumOf { it.kotlin.content.functionDeclarationCount },
        )

    private fun contentScores(
        counts: ContentCounts,
        matchedFileCount: Int,
        shapeMismatchFileCount: Int,
    ): ContentScores {
        val javaReturnDensity = density(counts.javaReturnCount, counts.javaFunctionDeclarationCount)
        val kotlinReturnDensity = density(counts.kotlinReturnCount, counts.kotlinFunctionDeclarationCount)
        val javaBranchComplexityIndex =
            branchComplexityIndex(
                counts.javaBranchCount,
                counts.javaLoopCount,
                counts.javaTryCount,
                counts.javaFunctionDeclarationCount,
            )
        val kotlinBranchComplexityIndex =
            branchComplexityIndex(
                counts.kotlinBranchCount,
                counts.kotlinLoopCount,
                counts.kotlinTryCount,
                counts.kotlinFunctionDeclarationCount,
            )

        return ContentScores(
            returnPreservationRatio = preservation(counts.kotlinReturnCount, counts.javaReturnCount),
            branchPreservationRatio = preservation(counts.kotlinBranchCount, counts.javaBranchCount),
            throwPreservationRatio = preservation(counts.kotlinThrowCount, counts.javaThrowCount),
            tryPreservationRatio = preservation(counts.kotlinTryCount, counts.javaTryCount),
            controlFlowFidelityScore = controlFlowFidelityScore(counts),
            contentShapePreservationRate = contentShapePreservationRate(matchedFileCount, shapeMismatchFileCount),
            javaReturnDensity = javaReturnDensity,
            kotlinReturnDensity = kotlinReturnDensity,
            returnStatementDensityPreservation =
                cappedRatio(kotlinReturnDensity, javaReturnDensity, PERFECT_PRESERVATION),
            javaBranchComplexityIndex = javaBranchComplexityIndex,
            kotlinBranchComplexityIndex = kotlinBranchComplexityIndex,
            branchComplexityIndexPreservation =
                cappedRatio(kotlinBranchComplexityIndex, javaBranchComplexityIndex, BRANCH_COMPLEXITY_PRESERVATION_CAP),
        )
    }

    private fun controlFlowFidelityScore(counts: ContentCounts): Double =
        preservation(counts.kotlinReturnCount, counts.javaReturnCount) * RETURN_PRESERVATION_WEIGHT +
            preservation(counts.kotlinBranchCount, counts.javaBranchCount) * BRANCH_PRESERVATION_WEIGHT +
            preservation(counts.kotlinThrowCount, counts.javaThrowCount) * THROW_PRESERVATION_WEIGHT +
            preservation(counts.kotlinTryCount, counts.javaTryCount) * TRY_PRESERVATION_WEIGHT

    private fun contentShapePreservationRate(
        matchedFileCount: Int,
        mismatchFileCount: Int,
    ): Double =
        if (matchedFileCount == 0) {
            PERFECT_PRESERVATION
        } else {
            (matchedFileCount - mismatchFileCount).toDouble() / matchedFileCount.toDouble()
        }

    private fun density(
        count: Int,
        functionDeclarationCount: Int,
    ): Double =
        if (functionDeclarationCount == 0) {
            0.0
        } else {
            count.toDouble() / functionDeclarationCount.toDouble()
        }

    private fun branchComplexityIndex(
        branchCount: Int,
        loopCount: Int,
        tryCount: Int,
        functionDeclarationCount: Int,
    ): Double = density(branchCount + loopCount + tryCount, functionDeclarationCount)

    private fun preservation(
        kotlinCount: Int,
        javaCount: Int,
    ): Double =
        if (javaCount == 0) {
            PERFECT_PRESERVATION
        } else {
            minOf(kotlinCount.toDouble() / javaCount.toDouble(), PERFECT_PRESERVATION)
        }

    private fun cappedRatio(
        numerator: Double,
        denominator: Double,
        cap: Double,
    ): Double =
        if (denominator == 0.0) {
            PERFECT_PRESERVATION
        } else {
            minOf(numerator / denominator, cap)
        }

    private fun SourceContentProfile.hasShapeMissingFrom(kotlin: SourceContentProfile): Boolean =
        (returnCount > 0 && kotlin.returnCount == 0) ||
            (branchCount > 0 && kotlin.branchCount == 0) ||
            (loopCount > 0 && kotlin.loopCount == 0) ||
            (throwCount > 0 && kotlin.throwCount == 0) ||
            (tryCount > 0 && kotlin.tryCount == 0) ||
            literalValues.minus(kotlin.literalValues).isNotEmpty()

    private fun SourceContentProfile.hasNonPropertyBackedExecutableMethods(propertyBackedAccessorNames: Set<String>): Boolean =
        nonEmptyFunctionNames.any { it !in propertyBackedAccessorNames }

    private fun contentFindings(
        missingBodies: List<String>,
        shapeMismatchFiles: List<String>,
    ): List<EvaluationWarning> =
        buildList {
            missingBodies
                .groupingBy { it.substringBefore(MEMBER_PATH_SEPARATOR) }
                .eachCount()
                .forEach { (path, count) ->
                    add(
                        EvaluationWarning(
                            code = "missing_kotlin_body",
                            message = "Generated Kotlin appears to be missing bodies for Java methods with executable bodies.",
                            path = path,
                            count = count,
                        ),
                    )
                }
            shapeMismatchFiles.forEach { path ->
                add(
                    EvaluationWarning(
                        code = "content_shape_mismatch",
                        message =
                            "Generated Kotlin lost one or more Java body-shape signals such as returns, " +
                                "branches, loops, throws, tries, or literals.",
                        path = path,
                    ),
                )
            }
        }

    private data class ContentCounts(
        val javaReturnCount: Int,
        val kotlinReturnCount: Int,
        val javaBranchCount: Int,
        val kotlinBranchCount: Int,
        val javaLoopCount: Int,
        val kotlinLoopCount: Int,
        val javaThrowCount: Int,
        val kotlinThrowCount: Int,
        val javaTryCount: Int,
        val kotlinTryCount: Int,
        val javaFunctionDeclarationCount: Int,
        val kotlinFunctionDeclarationCount: Int,
    )

    private data class ContentScores(
        val returnPreservationRatio: Double,
        val branchPreservationRatio: Double,
        val throwPreservationRatio: Double,
        val tryPreservationRatio: Double,
        val controlFlowFidelityScore: Double,
        val contentShapePreservationRate: Double,
        val javaReturnDensity: Double,
        val kotlinReturnDensity: Double,
        val returnStatementDensityPreservation: Double,
        val javaBranchComplexityIndex: Double,
        val kotlinBranchComplexityIndex: Double,
        val branchComplexityIndexPreservation: Double,
    )

    private companion object {
        private const val BRANCH_COMPLEXITY_PRESERVATION_CAP = 1.0
        private const val BRANCH_PRESERVATION_WEIGHT = 0.3
        private const val MEMBER_PATH_SEPARATOR = "#"
        private const val PERFECT_PRESERVATION = 1.0
        private const val RETURN_PRESERVATION_WEIGHT = 0.4
        private const val THROW_PRESERVATION_WEIGHT = 0.2
        private const val TRY_PRESERVATION_WEIGHT = 0.1
    }
}
