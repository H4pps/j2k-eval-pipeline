package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.source.DiscoveredSourceFile
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * Calculates deterministic evaluator metrics from discovered Java and Kotlin files.
 */
@Suppress("LargeClass")
class EvaluationMetricsCalculator(
    private val scanner: SourceTextScanner = SourceTextScanner(),
) {
    /**
     * Calculates file coverage, structural preservation, and Kotlin quality metrics.
     */
    fun calculate(
        javaFiles: List<DiscoveredSourceFile>,
        kotlinFiles: List<DiscoveredSourceFile>,
        sourceRoots: List<String>,
    ): EvaluationMetrics {
        val pathIndex = sourcePathIndex(javaFiles, kotlinFiles, sourceRoots)
        val matchedStructures = matchedStructures(pathIndex)
        val qualityFiles = kotlinFiles.map { scanQuality(it) }
        val structure = structure(matchedStructures)

        return EvaluationMetrics(
            fileCoverage = fileCoverage(javaFiles, kotlinFiles, pathIndex),
            structure = structure,
            content = content(matchedStructures),
            nullability = nullability(matchedStructures),
            quality = quality(qualityFiles),
        )
    }

    /**
     * Maps a checkout-relative Java path to the generated-output-relative Kotlin path.
     */
    fun expectedKotlinRelativePath(
        javaRelativePath: Path,
        sourceRoots: List<String>,
    ): Path {
        val normalizedJavaPath = javaRelativePath.normalize()
        val sourceRootRelativePath =
            sourceRoots
                .map { Path.of(it).normalize() }
                .firstOrNull { normalizedJavaPath.startsWith(it) }
                ?.relativize(normalizedJavaPath)
                ?: normalizedJavaPath
        val kotlinFileName = "${sourceRootRelativePath.nameWithoutExtension}.kt"
        return sourceRootRelativePath.parent?.resolve(kotlinFileName)?.normalize() ?: Path.of(kotlinFileName)
    }

    /**
     * Builds lookup tables for expected generated Kotlin paths.
     */
    private fun sourcePathIndex(
        javaFiles: List<DiscoveredSourceFile>,
        kotlinFiles: List<DiscoveredSourceFile>,
        sourceRoots: List<String>,
    ): SourcePathIndex {
        val javaByExpectedPath = javaFiles.associateBy { expectedKotlinRelativePath(it.relativePath, sourceRoots).toString() }
        val kotlinByPath = kotlinFiles.associateBy { it.relativePath.normalize().toString() }
        return SourcePathIndex(
            javaByExpectedPath = javaByExpectedPath,
            kotlinByPath = kotlinByPath,
            matchedPaths = javaByExpectedPath.keys.intersect(kotlinByPath.keys).sorted(),
            missingKotlinFiles = javaByExpectedPath.keys.minus(kotlinByPath.keys).sorted(),
            unexpectedKotlinFiles = kotlinByPath.keys.minus(javaByExpectedPath.keys).sorted(),
        )
    }

    /**
     * Calculates file coverage and package preservation metrics.
     */
    private fun fileCoverage(
        javaFiles: List<DiscoveredSourceFile>,
        kotlinFiles: List<DiscoveredSourceFile>,
        pathIndex: SourcePathIndex,
    ): FileCoverageMetrics {
        val packageMismatches = packageMismatches(pathIndex)
        return FileCoverageMetrics(
            javaFileCount = javaFiles.size,
            kotlinFileCount = kotlinFiles.size,
            matchedKotlinFileCount = pathIndex.matchedPaths.size,
            missingKotlinFiles = pathIndex.missingKotlinFiles,
            unexpectedKotlinFiles = pathIndex.unexpectedKotlinFiles,
            emptyGeneratedFiles = emptyGeneratedFiles(kotlinFiles),
            packagePreservedCount = pathIndex.matchedPaths.size - packageMismatches.size,
            packageMismatchFiles = packageMismatches,
        )
    }

    /**
     * Finds generated Kotlin files that do not preserve the Java package or package path.
     */
    private fun packageMismatches(pathIndex: SourcePathIndex): List<String> =
        pathIndex.matchedPaths.filterNot { relativeKotlinPath ->
            val javaStructure =
                scanner.scanJava(
                    pathIndex.javaByExpectedPath
                        .getValue(relativeKotlinPath)
                        .absolutePath
                        .readText(),
                )
            val kotlinStructure =
                scanner.scanKotlin(
                    pathIndex.kotlinByPath
                        .getValue(relativeKotlinPath)
                        .absolutePath
                        .readText(),
                )
            packageIsPreserved(relativeKotlinPath, javaStructure, kotlinStructure)
        }

    /**
     * Checks whether a matched output kept the source package declaration and path.
     */
    private fun packageIsPreserved(
        relativeKotlinPath: String,
        javaStructure: SourceStructure,
        kotlinStructure: SourceStructure,
    ): Boolean {
        val expectedPackagePath = javaStructure.packageName?.replace('.', '/')
        val actualPackagePath = Path.of(relativeKotlinPath).parent?.toString()
        return javaStructure.packageName == kotlinStructure.packageName &&
            (expectedPackagePath == null || actualPackagePath == expectedPackagePath)
    }

    /**
     * Finds blank generated Kotlin files.
     */
    private fun emptyGeneratedFiles(kotlinFiles: List<DiscoveredSourceFile>): List<String> =
        kotlinFiles
            .filter { it.absolutePath.readText().isBlank() }
            .map { it.relativePath.normalize().toString() }
            .sorted()

    /**
     * Calculates lightweight structural preservation metrics.
     */
    private fun structure(matchedStructures: List<MatchedSourceStructure>): StructuralMetrics {
        val javaStructures = matchedStructures.map { it.java }
        val kotlinStructures = matchedStructures.map { it.kotlin }
        val javaApiNames = javaStructures.flatMap { it.publicApiNames }.toSet()
        val kotlinApiNames = kotlinStructures.flatMap { it.publicApiNames }.toSet()
        val nameDiffs = structuralNameDiffs(javaStructures, kotlinStructures)
        val propertyBackedAccessorNames = nameDiffs.javaBeanAccessorNames.toSet()
        val propertyNamesBackingAccessors = propertyNamesBackingAccessors(javaStructures, kotlinStructures, propertyBackedAccessorNames)
        return StructuralMetrics(
            javaTopLevelDeclarationCount = javaStructures.sumOf { it.topLevelDeclarationCount },
            kotlinTopLevelDeclarationCount = kotlinStructures.sumOf { it.topLevelDeclarationCount },
            javaClassLikeCount = javaStructures.sumOf { it.classLikeCount },
            kotlinClassLikeCount = kotlinStructures.sumOf { it.classLikeCount },
            javaInterfaceCount = javaStructures.sumOf { it.interfaceCount },
            kotlinInterfaceCount = kotlinStructures.sumOf { it.interfaceCount },
            javaEnumCount = javaStructures.sumOf { it.enumCount },
            kotlinEnumCount = kotlinStructures.sumOf { it.enumCount },
            javaMethodCount = javaStructures.sumOf { it.functionNames.size },
            kotlinFunctionCount = kotlinStructures.sumOf { it.functionNames.size },
            publicApiNameOverlapCount = javaApiNames.intersect(kotlinApiNames).size + propertyBackedAccessorNames.size,
            missingPublicApiNames = javaApiNames.minus(kotlinApiNames).minus(propertyBackedAccessorNames).sorted(),
            kotlinOnlyPublicApiNames = kotlinApiNames.minus(javaApiNames).minus(propertyNamesBackingAccessors).sorted(),
            nameDiffs = nameDiffs,
        )
    }

    /**
     * Calculates structural name differences grouped by declaration/member kind.
     */
    private fun structuralNameDiffs(
        javaStructures: List<SourceStructure>,
        kotlinStructures: List<SourceStructure>,
    ): StructuralNameDiffs {
        val javaClassLikeNames = javaStructures.flatMap { it.classLikeNames }.toSet()
        val kotlinClassLikeNames = kotlinStructures.flatMap { it.classLikeNames }.toSet()
        val kotlinObjectNames = kotlinStructures.flatMap { it.objectNames }.toSet()
        val classLikeToObjectNames = javaClassLikeNames.intersect(kotlinObjectNames).sorted()
        val functionDiff =
            nameDiff(
                javaStructures.flatMap { it.functionNames },
                kotlinStructures.flatMap { it.functionNames },
            )
        val ownerScopedAccessorNames = ownerScopedPropertyBackedAccessorNames(javaStructures, kotlinStructures)
        val javaBeanAccessorNames =
            functionDiff
                .missingInKotlin
                .filter { it in ownerScopedAccessorNames }

        return StructuralNameDiffs(
            classLike =
                nameDiff(
                    javaClassLikeNames.minus(classLikeToObjectNames),
                    kotlinClassLikeNames,
                ),
            interfaces =
                nameDiff(
                    javaStructures.flatMap { it.interfaceNames },
                    kotlinStructures.flatMap { it.interfaceNames },
                ),
            enums =
                nameDiff(
                    javaStructures.flatMap { it.enumNames },
                    kotlinStructures.flatMap { it.enumNames },
                ),
            objects =
                nameDiff(
                    emptyList(),
                    kotlinObjectNames.minus(classLikeToObjectNames),
                ),
            classLikeToObjectNames = classLikeToObjectNames,
            javaBeanAccessorNames = javaBeanAccessorNames,
            functions =
                StructuralNameDiff(
                    missingInKotlin = functionDiff.missingInKotlin.minus(javaBeanAccessorNames),
                    kotlinOnly = functionDiff.kotlinOnly,
                ),
        )
    }

    /**
     * Finds accessor names whose implied properties exist on the same generated Kotlin type.
     */
    private fun ownerScopedPropertyBackedAccessorNames(
        javaStructures: List<SourceStructure>,
        kotlinStructures: List<SourceStructure>,
    ): Set<String> =
        javaStructures
            .flatMap { javaStructure ->
                kotlinStructures.flatMap { kotlinStructure -> propertyBackedAccessorNames(javaStructure, kotlinStructure) }
            }.toSet()

    /**
     * Finds JavaBean accessor names represented by Kotlin properties.
     */
    private fun propertyBackedAccessorNames(
        javaStructure: SourceStructure,
        kotlinStructure: SourceStructure,
    ): Set<String> {
        val kotlinPropertiesByOwner = kotlinStructure.kotlinPropertyRecords.groupBy { it.ownerName }
        return javaStructure.javaBeanAccessorRecords
            .filter { accessor ->
                val ownerProperties = kotlinPropertiesByOwner[accessor.ownerName].orEmpty()
                val topLevelProperties = kotlinPropertiesByOwner[null].orEmpty()
                accessor.isBackedBy(ownerProperties) ||
                    (accessor.isStatic && accessor.isBackedBy(topLevelProperties))
            }.map { it.methodName }
            .toSet()
    }

    /**
     * Returns whether a JavaBean accessor is represented by one of [properties].
     */
    private fun JavaBeanAccessorRecord.isBackedBy(properties: List<KotlinPropertyRecord>): Boolean =
        properties
            .filter { property -> !property.isPrivate || isPrivate }
            .any { property -> property.name in propertyNames }

    /**
     * Finds Kotlin property names backing JavaBean accessors that were matched by method name.
     */
    private fun propertyNamesBackingAccessors(
        javaStructures: List<SourceStructure>,
        kotlinStructures: List<SourceStructure>,
        propertyBackedAccessorNames: Set<String>,
    ): Set<String> {
        val kotlinPropertyNames = kotlinStructures.flatMap { it.propertyNames }.toSet()
        return javaStructures
            .flatMap { it.javaBeanAccessorPropertyNames.entries }
            .filter { (accessorName, _) -> accessorName in propertyBackedAccessorNames }
            .flatMap { (_, propertyNames) -> propertyNames.filter { it in kotlinPropertyNames } }
            .toSet()
    }

    /**
     * Calculates parser-backed body and control-flow preservation metrics across matched files.
     */
    @Suppress("LongMethod")
    private fun content(pairs: List<MatchedSourceStructure>): ContentMetrics {
        val counts = contentCounts(pairs)
        val missingBodies =
            pairs
                .flatMap { pair ->
                    val propertyBackedAccessorNames = propertyBackedAccessorNames(pair.java, pair.kotlin)
                    pair.java.content.nonEmptyFunctionNames
                        .filterNot { name -> name in pair.kotlin.content.nonEmptyFunctionNames || name in propertyBackedAccessorNames }
                        .map { name -> "${pair.path}#$name" }
                }.sorted()
        val shapeMismatchFiles =
            pairs
                .filter { pair ->
                    val propertyBackedAccessorNames = propertyBackedAccessorNames(pair.java, pair.kotlin)
                    pair.java.content.hasNonPropertyBackedExecutableMethods(propertyBackedAccessorNames) &&
                        pair.java.content.hasShapeMissingFrom(pair.kotlin.content)
                }.map { it.path }
                .sorted()

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
            returnPreservationRatio = preservation(counts.kotlinReturnCount, counts.javaReturnCount),
            branchPreservationRatio = preservation(counts.kotlinBranchCount, counts.javaBranchCount),
            throwPreservationRatio = preservation(counts.kotlinThrowCount, counts.javaThrowCount),
            tryPreservationRatio = preservation(counts.kotlinTryCount, counts.javaTryCount),
            controlFlowFidelityScore = controlFlowFidelityScore(counts),
            contentShapePreservationRate = contentShapePreservationRate(pairs.size, shapeMismatchFiles.size),
            javaReturnDensity = density(counts.javaReturnCount, counts.javaFunctionDeclarationCount),
            kotlinReturnDensity = density(counts.kotlinReturnCount, counts.kotlinFunctionDeclarationCount),
            returnStatementDensityPreservation =
                cappedRatio(
                    numerator = density(counts.kotlinReturnCount, counts.kotlinFunctionDeclarationCount),
                    denominator = density(counts.javaReturnCount, counts.javaFunctionDeclarationCount),
                    cap = PERFECT_PRESERVATION,
                ),
            javaBranchComplexityIndex =
                branchComplexityIndex(
                    counts.javaBranchCount,
                    counts.javaLoopCount,
                    counts.javaTryCount,
                    counts.javaFunctionDeclarationCount,
                ),
            kotlinBranchComplexityIndex =
                branchComplexityIndex(
                    counts.kotlinBranchCount,
                    counts.kotlinLoopCount,
                    counts.kotlinTryCount,
                    counts.kotlinFunctionDeclarationCount,
                ),
            branchComplexityIndexPreservation =
                cappedRatio(
                    numerator =
                        branchComplexityIndex(
                            counts.kotlinBranchCount,
                            counts.kotlinLoopCount,
                            counts.kotlinTryCount,
                            counts.kotlinFunctionDeclarationCount,
                        ),
                    denominator =
                        branchComplexityIndex(
                            counts.javaBranchCount,
                            counts.javaLoopCount,
                            counts.javaTryCount,
                            counts.javaFunctionDeclarationCount,
                        ),
                    cap = BRANCH_COMPLEXITY_PRESERVATION_CAP,
                ),
            findings = contentFindings(missingBodies, shapeMismatchFiles),
        )
    }

    /**
     * Aggregates parser-backed content counts across all matched files.
     */
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

    /**
     * Calculates parser-backed Java annotation to Kotlin nullable-type preservation metrics.
     */
    private fun nullability(pairs: List<MatchedSourceStructure>): NullabilityMetrics {
        val contradictoryNullabilityPatterns = pairs.sumOf { it.kotlin.nullability.contradictoryNullabilityPatternCount }
        val nullComparisonCount = pairs.sumOf { it.kotlin.nullability.nullComparisonCount }
        val nullabilityCastCount = pairs.sumOf { it.kotlin.nullability.nullabilityCastCount }
        val safeCallCount = pairs.sumOf { it.kotlin.nullability.safeCallCount }
        val totalNullabilityOperationCount = pairs.sumOf { it.kotlin.nullability.totalNullabilityOperationCount }
        val nullableNotPreserved =
            pairs
                .flatMap { pair ->
                    pair.java.nullability.nullableNames
                        .filterNot { name -> pair.java.nullabilityNameMapsToKotlinNullable(name, pair.kotlin.nullability) }
                        .map { name -> "${pair.path}#$name" }
                }.sorted()
        val notNullBecameNullable =
            pairs
                .flatMap { pair ->
                    pair.java.nullability.notNullNames
                        .filter { name -> pair.java.nullabilityNameMapsToKotlinNullable(name, pair.kotlin.nullability) }
                        .map { name -> "${pair.path}#$name" }
                }.sorted()

        return NullabilityMetrics(
            javaNullableAnnotationCount = pairs.sumOf { it.java.nullability.nullableAnnotationCount },
            javaNotNullAnnotationCount = pairs.sumOf { it.java.nullability.notNullAnnotationCount },
            kotlinNullableTypeCount = pairs.sumOf { it.kotlin.nullability.nullableTypeNames.size },
            contradictoryNullabilityPatterns = contradictoryNullabilityPatterns,
            nullComparisonCount = nullComparisonCount,
            nullabilityCastCount = nullabilityCastCount,
            safeCallCount = safeCallCount,
            totalNullabilityOperationCount = totalNullabilityOperationCount,
            nullabilityInferenceAccuracy =
                nullabilityInferenceAccuracy(
                    contradictoryPatterns = contradictoryNullabilityPatterns,
                    totalNullabilityOperations = totalNullabilityOperationCount,
                ),
            nullableAnnotationsNotPreserved = nullableNotPreserved,
            notNullAnnotationsBecameNullable = notNullBecameNullable,
            findings = nullabilityFindings(nullableNotPreserved, notNullBecameNullable),
        )
    }

    /**
     * Calculates weighted preservation of return, branch, throw, and try control-flow signals.
     */
    private fun controlFlowFidelityScore(counts: ContentCounts): Double =
        preservation(counts.kotlinReturnCount, counts.javaReturnCount) * RETURN_PRESERVATION_WEIGHT +
            preservation(counts.kotlinBranchCount, counts.javaBranchCount) * BRANCH_PRESERVATION_WEIGHT +
            preservation(counts.kotlinThrowCount, counts.javaThrowCount) * THROW_PRESERVATION_WEIGHT +
            preservation(counts.kotlinTryCount, counts.javaTryCount) * TRY_PRESERVATION_WEIGHT

    /**
     * Calculates the share of matched files without content-shape mismatches.
     */
    private fun contentShapePreservationRate(
        matchedFileCount: Int,
        mismatchFileCount: Int,
    ): Double =
        if (matchedFileCount == 0) {
            PERFECT_PRESERVATION
        } else {
            (matchedFileCount - mismatchFileCount).toDouble() / matchedFileCount.toDouble()
        }

    /**
     * Calculates per-function density for one count family.
     */
    private fun density(
        count: Int,
        functionDeclarationCount: Int,
    ): Double =
        if (functionDeclarationCount == 0) {
            0.0
        } else {
            count.toDouble() / functionDeclarationCount.toDouble()
        }

    /**
     * Calculates conditional complexity per function.
     */
    private fun branchComplexityIndex(
        branchCount: Int,
        loopCount: Int,
        tryCount: Int,
        functionDeclarationCount: Int,
    ): Double = density(branchCount + loopCount + tryCount, functionDeclarationCount)

    /**
     * Calculates capped Kotlin-over-Java preservation for integer counts.
     */
    private fun preservation(
        kotlinCount: Int,
        javaCount: Int,
    ): Double =
        if (javaCount == 0) {
            PERFECT_PRESERVATION
        } else {
            minOf(kotlinCount.toDouble() / javaCount.toDouble(), PERFECT_PRESERVATION)
        }

    /**
     * Calculates a capped ratio while treating a zero denominator as perfect preservation.
     */
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

    /**
     * Calculates nullability consistency from contradictory patterns and all nullability operations.
     */
    private fun nullabilityInferenceAccuracy(
        contradictoryPatterns: Int,
        totalNullabilityOperations: Int,
    ): Double =
        if (totalNullabilityOperations == 0) {
            PERFECT_PRESERVATION
        } else {
            PERFECT_PRESERVATION - contradictoryPatterns.toDouble() / totalNullabilityOperations.toDouble()
        }

    /**
     * Builds a deterministic two-way name diff.
     */
    private fun nameDiff(
        javaNames: Collection<String>,
        kotlinNames: Collection<String>,
    ): StructuralNameDiff {
        val javaSet = javaNames.toSet()
        val kotlinSet = kotlinNames.toSet()
        return StructuralNameDiff(
            missingInKotlin = javaSet.minus(kotlinSet).sorted(),
            kotlinOnly = kotlinSet.minus(javaSet).sorted(),
        )
    }

    /**
     * Reads and scans all matched Java/Kotlin file pairs.
     */
    private fun matchedStructures(pathIndex: SourcePathIndex): List<MatchedSourceStructure> =
        pathIndex.matchedPaths.map { relativeKotlinPath ->
            MatchedSourceStructure(
                path = relativeKotlinPath,
                java =
                    scanner.scanJava(
                        pathIndex.javaByExpectedPath
                            .getValue(relativeKotlinPath)
                            .absolutePath
                            .readText(),
                    ),
                kotlin =
                    scanner.scanKotlin(
                        pathIndex.kotlinByPath
                            .getValue(relativeKotlinPath)
                            .absolutePath
                            .readText(),
                    ),
            )
        }

    /**
     * Returns whether generated Kotlin lost important Java body-shape signals.
     */
    private fun SourceContentProfile.hasShapeMissingFrom(kotlin: SourceContentProfile): Boolean =
        (returnCount > 0 && kotlin.returnCount == 0) ||
            (branchCount > 0 && kotlin.branchCount == 0) ||
            (loopCount > 0 && kotlin.loopCount == 0) ||
            (throwCount > 0 && kotlin.throwCount == 0) ||
            (tryCount > 0 && kotlin.tryCount == 0) ||
            literalValues.minus(kotlin.literalValues).isNotEmpty()

    /**
     * Returns whether any executable Java method is not represented by a Kotlin property.
     */
    private fun SourceContentProfile.hasNonPropertyBackedExecutableMethods(propertyBackedAccessorNames: Set<String>): Boolean =
        nonEmptyFunctionNames.any { it !in propertyBackedAccessorNames }

    /**
     * Returns whether a Java nullable/not-null name maps to a nullable Kotlin declaration.
     */
    private fun SourceStructure.nullabilityNameMapsToKotlinNullable(
        name: String,
        kotlinNullability: SourceNullabilityProfile,
    ): Boolean =
        name in kotlinNullability.nullableTypeNames ||
            javaBeanAccessorPropertyNames[name].orEmpty().any { it in kotlinNullability.nullableTypeNames }

    /**
     * Builds content findings grouped by generated Kotlin file.
     */
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

    /**
     * Builds nullability findings grouped by generated Kotlin file.
     */
    private fun nullabilityFindings(
        nullableNotPreserved: List<String>,
        notNullBecameNullable: List<String>,
    ): List<EvaluationWarning> =
        buildList {
            nullableNotPreserved
                .groupingBy { it.substringBefore(MEMBER_PATH_SEPARATOR) }
                .eachCount()
                .forEach { (path, count) ->
                    add(
                        EvaluationWarning(
                            code = "nullable_annotation_not_preserved",
                            message = "Java nullable annotations were not reflected as nullable Kotlin declarations.",
                            path = path,
                            count = count,
                        ),
                    )
                }
            notNullBecameNullable
                .groupingBy { it.substringBefore(MEMBER_PATH_SEPARATOR) }
                .eachCount()
                .forEach { (path, count) ->
                    add(
                        EvaluationWarning(
                            code = "not_null_annotation_became_nullable",
                            message = "Java not-null annotations were converted to nullable Kotlin declarations.",
                            path = path,
                            count = count,
                        ),
                    )
                }
        }

    /**
     * Scans quality warnings for one generated Kotlin file.
     */
    private fun scanQuality(file: DiscoveredSourceFile): QualityFileMetrics =
        scanner.scanKotlinQuality(
            path = file.relativePath.normalize().toString(),
            source = file.absolutePath.readText(),
        )

    /**
     * Aggregates per-file Kotlin quality warning metrics.
     */
    private fun quality(qualityFiles: List<QualityFileMetrics>): QualityMetrics =
        QualityMetrics(
            todoCount = qualityFiles.sumOf { it.todoCount },
            notNullAssertionCount = qualityFiles.sumOf { it.notNullAssertionCount },
            notNullAssertionInCallCount = qualityFiles.sumOf { it.notNullAssertionInCallCount },
            anyNullableCount = qualityFiles.sumOf { it.anyNullableCount },
            unresolvedImportCount = qualityFiles.sumOf { it.unresolvedImportCount },
            javaInteropReferenceCount = qualityFiles.sumOf { it.javaInteropReferenceCount },
            getterSetterCallCount = qualityFiles.sumOf { it.getterSetterCallCount },
            nullableBooleanComparisonCount = qualityFiles.sumOf { it.nullableBooleanComparisonCount },
            eagerPropertyInitializationCount = qualityFiles.sumOf { it.eagerPropertyInitializationCount },
            findings = qualityFiles.flatMap { it.findings },
        )
}

/**
 * Path lookups used by file coverage calculations.
 */
private data class SourcePathIndex(
    val javaByExpectedPath: Map<String, DiscoveredSourceFile>,
    val kotlinByPath: Map<String, DiscoveredSourceFile>,
    val matchedPaths: List<String>,
    val missingKotlinFiles: List<String>,
    val unexpectedKotlinFiles: List<String>,
)

/**
 * Grouped metric families calculated by the evaluator.
 */
data class EvaluationMetrics(
    val fileCoverage: FileCoverageMetrics,
    val structure: StructuralMetrics,
    val content: ContentMetrics,
    val nullability: NullabilityMetrics,
    val quality: QualityMetrics,
)

/**
 * Java/Kotlin parser structures for one matched generated file.
 */
private data class MatchedSourceStructure(
    val path: String,
    val java: SourceStructure,
    val kotlin: SourceStructure,
)

/**
 * Aggregate content counts used by score calculations.
 */
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

private const val BRANCH_COMPLEXITY_PRESERVATION_CAP = 1.2
private const val BRANCH_PRESERVATION_WEIGHT = 0.3
private const val MEMBER_PATH_SEPARATOR = "#"
private const val PERFECT_PRESERVATION = 1.0
private const val RETURN_PRESERVATION_WEIGHT = 0.4
private const val THROW_PRESERVATION_WEIGHT = 0.2
private const val TRY_PRESERVATION_WEIGHT = 0.1
