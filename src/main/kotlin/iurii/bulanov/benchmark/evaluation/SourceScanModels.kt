package iurii.bulanov.benchmark.evaluation

/**
 * Kotlin property name with its nearest containing type, if any.
 */
data class KotlinPropertyRecord(
    val ownerName: String?,
    val name: String,
    val isPrivate: Boolean,
)

/**
 * JavaBean accessor mapping with its nearest containing Java type.
 */
data class JavaBeanAccessorRecord(
    val ownerName: String,
    val methodName: String,
    val propertyNames: Set<String>,
    val isPrivate: Boolean,
    val isStatic: Boolean,
)

/**
 * Structural data extracted from one source file.
 */
data class SourceStructure(
    val packageName: String?,
    val classLikeCount: Int,
    val interfaceCount: Int,
    val enumCount: Int,
    val objectCount: Int,
    val classLikeNames: Set<String>,
    val interfaceNames: Set<String>,
    val enumNames: Set<String>,
    val objectNames: Set<String>,
    val topLevelNames: Set<String>,
    val functionNames: Set<String>,
    val propertyNames: Set<String>,
    val propertyNamesByOwner: Map<String, Set<String>>,
    val javaBeanAccessorPropertyNames: Map<String, Set<String>>,
    val javaBeanAccessorPropertyNamesByOwner: Map<String, Map<String, Set<String>>>,
    val kotlinPropertyRecords: List<KotlinPropertyRecord>,
    val javaBeanAccessorRecords: List<JavaBeanAccessorRecord>,
    val content: SourceContentProfile,
    val nullability: SourceNullabilityProfile,
    val publicApiNames: Set<String>,
) {
    /**
     * Count of class/interface/enum/object-like declarations.
     */
    val topLevelDeclarationCount: Int = classLikeCount + interfaceCount + enumCount + objectCount
}

/**
 * Parser-backed body and control-flow signals for one source file.
 */
data class SourceContentProfile(
    val nonEmptyFunctionNames: Set<String>,
    val emptyFunctionNames: Set<String>,
    val functionDeclarationCount: Int,
    val returnCount: Int,
    val branchCount: Int,
    val loopCount: Int,
    val throwCount: Int,
    val tryCount: Int,
    val literalValues: Set<String>,
)

/**
 * Parser-backed nullability signals for one source file.
 */
data class SourceNullabilityProfile(
    val nullableNames: Set<String>,
    val notNullNames: Set<String>,
    val nullableAnnotationCount: Int,
    val notNullAnnotationCount: Int,
    val nullableTypeNames: Set<String>,
    val contradictoryNullabilityPatternCount: Int,
    val nullComparisonCount: Int,
    val nullabilityCastCount: Int,
    val safeCallCount: Int,
    val totalNullabilityOperationCount: Int,
)

/**
 * Quality metrics extracted from one Kotlin source file.
 */
data class QualityFileMetrics(
    val todoCount: Int,
    val notNullAssertionCount: Int,
    val notNullAssertionInCallCount: Int,
    val anyNullableCount: Int,
    val unresolvedImportCount: Int,
    val javaInteropReferenceCount: Int,
    val getterSetterCallCount: Int,
    val nullableBooleanComparisonCount: Int,
    val eagerPropertyInitializationCount: Int,
    val findings: List<EvaluationWarning>,
)
