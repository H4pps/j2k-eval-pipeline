package iurii.bulanov.benchmark.evaluation.metrics

import iurii.bulanov.benchmark.evaluation.JavaBeanAccessorRecord
import iurii.bulanov.benchmark.evaluation.KotlinPropertyRecord
import iurii.bulanov.benchmark.evaluation.SourceStructure

/**
 * Matches JavaBean accessor methods to Kotlin properties on equivalent owners.
 */
internal class JavaBeanPropertyMatcher {
    /**
     * Finds JavaBean accessor names represented by Kotlin properties.
     */
    fun propertyBackedAccessorNames(
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
     * Finds accessor names whose implied properties exist on the same generated Kotlin type.
     */
    fun ownerScopedPropertyBackedAccessorNames(
        javaStructures: List<SourceStructure>,
        kotlinStructures: List<SourceStructure>,
    ): Set<String> =
        javaStructures
            .flatMap { javaStructure ->
                kotlinStructures.flatMap { kotlinStructure -> propertyBackedAccessorNames(javaStructure, kotlinStructure) }
            }.toSet()

    /**
     * Finds Kotlin property names backing JavaBean accessors that were matched by method name.
     */
    fun propertyNamesBackingAccessors(
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

    private fun JavaBeanAccessorRecord.isBackedBy(properties: List<KotlinPropertyRecord>): Boolean =
        properties
            .filter { property -> !property.isPrivate || isPrivate }
            .any { property -> property.name in propertyNames }
}
