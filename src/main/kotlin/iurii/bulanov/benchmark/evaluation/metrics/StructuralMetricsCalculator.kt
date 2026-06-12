package iurii.bulanov.benchmark.evaluation.metrics

import iurii.bulanov.benchmark.evaluation.SourceStructure
import iurii.bulanov.benchmark.evaluation.StructuralMetrics
import iurii.bulanov.benchmark.evaluation.StructuralNameDiff
import iurii.bulanov.benchmark.evaluation.StructuralNameDiffs

/**
 * Calculates lightweight structural preservation metrics from matched parser structures.
 */
internal class StructuralMetricsCalculator(
    private val javaBeanPropertyMatcher: JavaBeanPropertyMatcher = JavaBeanPropertyMatcher(),
) : MatchedSourceMetricsCalculator<StructuralMetrics> {
    /**
     * Calculates structural declaration, API overlap, and name-difference metrics.
     */
    override fun calculate(pairs: List<MatchedSourceStructure>): StructuralMetrics {
        val matchedStructures = pairs
        val javaStructures = matchedStructures.map { it.java }
        val kotlinStructures = matchedStructures.map { it.kotlin }
        val javaApiNames = javaStructures.flatMap { it.publicApiNames }.toSet()
        val kotlinApiNames = kotlinStructures.flatMap { it.publicApiNames }.toSet()
        val nameDiffs = structuralNameDiffs(javaStructures, kotlinStructures)
        val propertyBackedAccessorNames = nameDiffs.javaBeanAccessorNames.toSet()
        val propertyNamesBackingAccessors =
            javaBeanPropertyMatcher.propertyNamesBackingAccessors(
                javaStructures,
                kotlinStructures,
                propertyBackedAccessorNames,
            )
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
        val ownerScopedAccessorNames =
            javaBeanPropertyMatcher.ownerScopedPropertyBackedAccessorNames(javaStructures, kotlinStructures)
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
}
