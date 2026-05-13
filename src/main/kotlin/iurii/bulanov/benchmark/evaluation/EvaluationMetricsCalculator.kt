package iurii.bulanov.benchmark.evaluation

import iurii.bulanov.source.DiscoveredSourceFile
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * Calculates deterministic evaluator metrics from discovered Java and Kotlin files.
 */
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
        val javaStructures = javaFiles.map { scanner.scanJava(it.absolutePath.readText()) }
        val kotlinStructures = kotlinFiles.map { scanner.scanKotlin(it.absolutePath.readText()) }
        val qualityFiles = kotlinFiles.map { scanQuality(it) }
        val structure = structure(javaStructures, kotlinStructures)

        return EvaluationMetrics(
            fileCoverage = fileCoverage(javaFiles, kotlinFiles, pathIndex),
            structure = structure,
            content = content(pathIndex, structure.nameDiffs.javaBeanAccessorNames.toSet()),
            nullability = nullability(pathIndex),
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
    private fun structure(
        javaStructures: List<SourceStructure>,
        kotlinStructures: List<SourceStructure>,
    ): StructuralMetrics {
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
    ): Set<String> {
        val kotlinPropertiesByOwner = kotlinStructures.mergedPropertyNamesByOwner()
        return javaStructures
            .flatMap { it.javaBeanAccessorPropertyNamesByOwner.entries }
            .flatMap { (ownerName, accessors) ->
                val propertyNames = kotlinPropertiesByOwner[ownerName].orEmpty()
                accessors.entries.filter { (_, candidates) -> candidates.any(propertyNames::contains) }.map { it.key }
            }.toSet()
    }

    /**
     * Finds Kotlin property names backing JavaBean accessors that were matched by method name.
     */
    private fun propertyNamesBackingAccessors(
        javaStructures: List<SourceStructure>,
        kotlinStructures: List<SourceStructure>,
        accessorNames: Set<String>,
    ): Set<String> {
        val kotlinPropertiesByOwner = kotlinStructures.mergedPropertyNamesByOwner()
        return javaStructures
            .flatMap { it.javaBeanAccessorPropertyNamesByOwner.entries }
            .flatMap { (ownerName, accessors) ->
                val propertyNames = kotlinPropertiesByOwner[ownerName].orEmpty()
                accessors
                    .filterKeys { it in accessorNames }
                    .values
                    .flatten()
                    .filter { it in propertyNames }
            }.toSet()
    }

    /**
     * Merges owner-scoped Kotlin property names across generated files.
     */
    private fun List<SourceStructure>.mergedPropertyNamesByOwner(): Map<String, Set<String>> =
        flatMap { it.propertyNamesByOwner.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, propertySets) -> propertySets.flatten().toSet() }

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
     * Calculates parser-backed body and control-flow preservation metrics across matched files.
     */
    private fun content(
        pathIndex: SourcePathIndex,
        propertyBackedAccessorNames: Set<String>,
    ): ContentMetrics {
        val pairs = matchedStructures(pathIndex)
        val missingBodies =
            pairs
                .flatMap { pair ->
                    pair.java.content.nonEmptyFunctionNames
                        .filterNot { name -> name in pair.kotlin.content.nonEmptyFunctionNames || name in propertyBackedAccessorNames }
                        .map { name -> "${pair.path}#$name" }
                }.sorted()
        val shapeMismatchFiles =
            pairs
                .filter { pair ->
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
            findings = contentFindings(missingBodies, shapeMismatchFiles),
        )
    }

    /**
     * Calculates parser-backed Java annotation to Kotlin nullable-type preservation metrics.
     */
    private fun nullability(pathIndex: SourcePathIndex): NullabilityMetrics {
        val pairs = matchedStructures(pathIndex)
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
            nullableAnnotationsNotPreserved = nullableNotPreserved,
            notNullAnnotationsBecameNullable = notNullBecameNullable,
            findings = nullabilityFindings(nullableNotPreserved, notNullBecameNullable),
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

private const val MEMBER_PATH_SEPARATOR = "#"
