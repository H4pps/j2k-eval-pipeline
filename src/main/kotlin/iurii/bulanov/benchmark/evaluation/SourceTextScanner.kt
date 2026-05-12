package iurii.bulanov.benchmark.evaluation

import com.github.javaparser.JavaParser
import com.github.javaparser.ParseStart
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.Providers
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.RecordDeclaration
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Extracts deterministic structural and quality signals from source text using language parsers.
 */
class SourceTextScanner {
    private val javaParser = JavaParser(ParserConfiguration())
    private val kotlinDisposable = Disposer.newDisposable()
    private val kotlinPsiFactory: KtPsiFactory by lazy { KtPsiFactory(kotlinEnvironment().project, markGenerated = false) }

    /**
     * Scans Java source text for package, declarations, methods, and JavaBean accessor mappings.
     */
    fun scanJava(source: String): SourceStructure {
        val unit = parseJava(source)
        val classes = unit.findAll(ClassOrInterfaceDeclaration::class.java)
        val classNames = classes.filterNot { it.isInterface }.map { it.nameAsString }.toSet()
        val interfaceNames = classes.filter { it.isInterface }.map { it.nameAsString }.toSet()
        val recordNames = unit.findAll(RecordDeclaration::class.java).map { it.nameAsString }.toSet()
        val enumNames = unit.findAll(EnumDeclaration::class.java).map { it.nameAsString }.toSet()
        val methodDeclarations = unit.findAll(MethodDeclaration::class.java)
        val methodNames = methodDeclarations.map { it.nameAsString }.toSet()
        val declarationNames = classNames + recordNames + interfaceNames + enumNames
        val javaBeanAccessorPropertyNamesByOwner = methodDeclarations.javaBeanAccessorPropertyNamesByOwner()

        return SourceStructure(
            packageName = unit.packageDeclaration.map { it.nameAsString }.orElse(null),
            classLikeCount = classNames.size + recordNames.size,
            interfaceCount = interfaceNames.size,
            enumCount = enumNames.size,
            objectCount = 0,
            classLikeNames = classNames + recordNames,
            interfaceNames = interfaceNames,
            enumNames = enumNames,
            objectNames = emptySet(),
            topLevelNames = declarationNames,
            functionNames = methodNames,
            propertyNames = emptySet(),
            propertyNamesByOwner = emptyMap(),
            javaBeanAccessorPropertyNames = javaBeanAccessorPropertyNamesByOwner.flattenValues(),
            javaBeanAccessorPropertyNamesByOwner = javaBeanAccessorPropertyNamesByOwner,
            publicApiNames = declarationNames + methodNames,
        )
    }

    /**
     * Scans Kotlin source text for package, declarations, functions, and properties.
     */
    fun scanKotlin(source: String): SourceStructure {
        val file = parseKotlin(source)
        val classes = file.collectDescendantsOfType<KtClass>().filterNot { it is KtEnumEntry }
        val classNames = classes.filter { !it.isInterfaceClass() && !it.isEnumClass() }.mapNotNull { it.name }.toSet()
        val interfaceNames = classes.filter { it.isInterfaceClass() }.mapNotNull { it.name }.toSet()
        val enumNames = classes.filter { it.isEnumClass() }.mapNotNull { it.name }.toSet()
        val objectNames =
            file
                .collectDescendantsOfType<KtObjectDeclaration>()
                .filterNot { it.isCompanion() }
                .mapNotNull { it.name }
                .toSet()
        val functionNames = file.collectDescendantsOfType<KtNamedFunction>().mapNotNull { it.name }.toSet()
        val propertyRecords = file.kotlinPropertyRecords()
        val propertyNames = propertyRecords.map { it.name }.toSet()
        val declarationNames = classNames + interfaceNames + enumNames + objectNames

        return SourceStructure(
            packageName = file.packageFqName.asString().ifEmpty { null },
            classLikeCount = classNames.size,
            interfaceCount = interfaceNames.size,
            enumCount = enumNames.size,
            objectCount = objectNames.size,
            classLikeNames = classNames,
            interfaceNames = interfaceNames,
            enumNames = enumNames,
            objectNames = objectNames,
            topLevelNames = declarationNames,
            functionNames = functionNames,
            propertyNames = propertyNames,
            propertyNamesByOwner = propertyRecords.groupByOwner(),
            javaBeanAccessorPropertyNames = emptyMap(),
            javaBeanAccessorPropertyNamesByOwner = emptyMap(),
            publicApiNames = declarationNames + functionNames + propertyNames,
        )
    }

    /**
     * Scans Kotlin source text for mechanical conversion quality warnings.
     */
    fun scanKotlinQuality(
        path: String,
        source: String,
    ): QualityFileMetrics {
        val file = parseKotlin(source)
        val counts = qualityCounts(file)
        return QualityFileMetrics(
            todoCount = counts.todoCount,
            notNullAssertionCount = counts.notNullAssertionCount,
            notNullAssertionInCallCount = counts.notNullAssertionInCallCount,
            anyNullableCount = counts.anyNullableCount,
            unresolvedImportCount = counts.unresolvedImportCount,
            javaInteropReferenceCount = counts.javaInteropReferenceCount,
            getterSetterCallCount = counts.getterSetterCallCount,
            nullableBooleanComparisonCount = counts.nullableBooleanComparisonCount,
            eagerPropertyInitializationCount = counts.eagerPropertyInitializationCount,
            findings = qualityFindings(counts, path),
        )
    }

    /**
     * Parses Java source or fails with JavaParser diagnostics.
     */
    private fun parseJava(source: String): CompilationUnit {
        val result = javaParser.parse(ParseStart.COMPILATION_UNIT, Providers.provider(source))
        return result.result.orElseThrow {
            IllegalArgumentException("Unable to parse Java source for evaluation: ${result.problems.joinToString()}")
        }
    }

    /**
     * Parses Kotlin source into PSI without semantic analysis.
     */
    private fun parseKotlin(source: String): KtFile = kotlinPsiFactory.createFile(KOTLIN_SYNTHETIC_FILE_NAME, source)

    /**
     * Creates the minimal Kotlin compiler environment needed by [KtPsiFactory].
     */
    @OptIn(K1Deprecation::class)
    private fun kotlinEnvironment(): KotlinCoreEnvironment {
        val configuration = CompilerConfiguration()
        configuration.put(CommonConfigurationKeys.MODULE_NAME, KOTLIN_SYNTHETIC_MODULE_NAME)
        return KotlinCoreEnvironment.createForProduction(kotlinDisposable, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES)
    }

    /**
     * Counts all Kotlin quality warning categories from PSI nodes.
     */
    private fun qualityCounts(file: KtFile): KotlinQualityCounts =
        KotlinQualityCounts(
            todoCount = file.collectDescendantsOfType<KtCallExpression>().count { it.isTodoCall() },
            notNullAssertionCount = file.collectDescendantsOfType<KtPostfixExpression>().count { it.isNotNullAssertion() },
            notNullAssertionInCallCount = file.collectDescendantsOfType<KtCallExpression>().count { it.hasNotNullArgument() },
            anyNullableCount = file.collectDescendantsOfType<KtNullableType>().count { it.isAnyNullable() },
            unresolvedImportCount = file.importDirectives.count { it.text.containsAny(UNRESOLVED_IMPORT_MARKERS) },
            javaInteropReferenceCount = file.collectDescendantsOfType<KtDotQualifiedExpression>().count { it.isJavaInteropReference() },
            getterSetterCallCount = file.collectDescendantsOfType<KtCallExpression>().count { it.isGetterSetterCall() },
            nullableBooleanComparisonCount =
                file
                    .collectDescendantsOfType<KtBinaryExpression>()
                    .count { it.isNullableBooleanComparison() },
            eagerPropertyInitializationCount =
                file
                    .collectDescendantsOfType<KtProperty>()
                    .count { it.isEagerPropertyInitialization() },
        )

    /**
     * Builds quality warning records from counted patterns.
     */
    private fun qualityFindings(
        counts: KotlinQualityCounts,
        path: String,
    ): List<EvaluationWarning> =
        buildList {
            addQualityFinding(counts.todoCount, "todo_call", "Generated Kotlin contains TODO() calls.", path)
            addQualityFinding(counts.notNullAssertionCount, "not_null_assertion", "Generated Kotlin contains not-null assertions.", path)
            addQualityFinding(
                counts.notNullAssertionInCallCount,
                "not_null_assertion_in_call",
                "Generated Kotlin contains not-null assertions inside function-call arguments.",
                path,
            )
            addQualityFinding(counts.anyNullableCount, "any_nullable", "Generated Kotlin contains Any? types.", path)
            addQualityFinding(
                counts.unresolvedImportCount,
                "unresolved_looking_import",
                "Generated Kotlin contains unresolved-looking imports.",
                path,
            )
            addQualityFinding(
                counts.javaInteropReferenceCount,
                "java_interop_leftover",
                "Generated Kotlin contains Java interop leftovers.",
                path,
            )
            addQualityFinding(
                counts.getterSetterCallCount,
                "getter_setter_call_leftover",
                "Generated Kotlin contains Java-style getter/setter calls.",
                path,
            )
            addQualityFinding(
                counts.nullableBooleanComparisonCount,
                "nullable_boolean_comparison",
                "Generated Kotlin contains nullable boolean comparisons that may change Java semantics.",
                path,
            )
            addQualityFinding(
                counts.eagerPropertyInitializationCount,
                "eager_property_initialization",
                "Generated Kotlin property initialization may need a custom getter.",
                path,
            )
        }

    /**
     * Adds a finding when [count] is nonzero.
     */
    private fun MutableList<EvaluationWarning>.addQualityFinding(
        count: Int,
        code: String,
        message: String,
        path: String,
    ) {
        if (count > 0) {
            this += EvaluationWarning(code = code, message = message, path = path, count = count)
        }
    }

    /**
     * Collects public Kotlin member, top-level, and primary-constructor property records.
     */
    private fun KtFile.kotlinPropertyRecords(): List<KotlinPropertyRecord> {
        val declaredProperties =
            collectDescendantsOfType<KtProperty>()
                .filterNot { it.hasModifier(KtTokens.PRIVATE_KEYWORD) }
                .filter { it.parent is KtFile || it.parent is KtClassBody }
                .mapNotNull { property -> property.name?.let { KotlinPropertyRecord(property.containingTypeName(), it) } }
        val constructorProperties =
            collectDescendantsOfType<KtParameter>()
                .filterNot { it.hasModifier(KtTokens.PRIVATE_KEYWORD) }
                .filter { it.hasValOrVar() }
                .mapNotNull { parameter -> parameter.name?.let { KotlinPropertyRecord(parameter.containingTypeName(), it) } }
        return declaredProperties + constructorProperties
    }

    /**
     * Groups Kotlin property names by containing type.
     */
    private fun List<KotlinPropertyRecord>.groupByOwner(): Map<String, Set<String>> =
        groupBy { it.ownerName ?: TOP_LEVEL_OWNER }
            .mapValues { (_, properties) -> properties.map { it.name }.toSet() }

    /**
     * Finds the nearest containing Kotlin class or object name.
     */
    private fun KtElement.containingTypeName(): String? {
        var current = parent
        while (current != null) {
            when (current) {
                is KtClass -> return current.name
                is KtObjectDeclaration -> return current.name
            }
            current = current.parent
        }
        return null
    }

    /**
     * Maps JavaBean accessor method names to implied Kotlin property names by containing type.
     */
    private fun List<MethodDeclaration>.javaBeanAccessorPropertyNamesByOwner(): Map<String, Map<String, Set<String>>> =
        mapNotNull { method ->
            val ownerName = method.containingTypeName() ?: return@mapNotNull null
            val propertyNames = method.javaBeanAccessorPropertyNames().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            JavaBeanAccessorRecord(ownerName, method.nameAsString, propertyNames)
        }.groupBy { it.ownerName }
            .mapValues { (_, accessors) -> accessors.associate { it.methodName to it.propertyNames } }

    /**
     * Finds the nearest containing Java type declaration name.
     */
    private fun MethodDeclaration.containingTypeName(): String? {
        var current: Node? = parentNode.orElse(null)
        while (current != null) {
            when (current) {
                is ClassOrInterfaceDeclaration -> return current.nameAsString
                is RecordDeclaration -> return current.nameAsString
                is EnumDeclaration -> return current.nameAsString
            }
            current = current.parentNode.orElse(null)
        }
        return null
    }

    /**
     * Flattens owner-scoped accessor mappings into the legacy method-name mapping.
     */
    private fun Map<String, Map<String, Set<String>>>.flattenValues(): Map<String, Set<String>> =
        values
            .flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, propertySets) -> propertySets.flatten().toSet() }

    /**
     * Returns possible Kotlin property names for a JavaBean accessor method when the signature matches.
     */
    private fun MethodDeclaration.javaBeanAccessorPropertyNames(): Set<String> =
        when {
            nameAsString.startsWith(GETTER_PREFIX) && parameters.isEmpty() ->
                nameAsString
                    .removePrefix(GETTER_PREFIX)
                    .takeIf { it.hasBeanPropertyStem() }
                    ?.let { setOf(it.decapitalizeBeanProperty()) }
                    .orEmpty()
            nameAsString.startsWith(SETTER_PREFIX) && parameters.size == SETTER_PARAMETER_COUNT ->
                nameAsString
                    .removePrefix(SETTER_PREFIX)
                    .takeIf { it.hasBeanPropertyStem() }
                    ?.let { setOf(it.decapitalizeBeanProperty()) }
                    .orEmpty()
            nameAsString.startsWith(BOOLEAN_GETTER_PREFIX) && parameters.isEmpty() ->
                nameAsString
                    .removePrefix(BOOLEAN_GETTER_PREFIX)
                    .takeIf { it.hasBeanPropertyStem() }
                    ?.let { setOf(nameAsString, it.decapitalizeBeanProperty()) }
                    .orEmpty()
            else -> emptySet()
        }

    /**
     * Returns whether this string can be a JavaBean property suffix.
     */
    private fun String.hasBeanPropertyStem(): Boolean = isNotEmpty() && first().isUpperCase()

    /**
     * Applies JavaBeans-style decapitalization to a getter/setter suffix.
     */
    private fun String.decapitalizeBeanProperty(): String =
        if (length > 1 && this[0].isUpperCase() && this[1].isUpperCase()) {
            this
        } else {
            replaceFirstChar { it.lowercaseChar() }
        }

    /**
     * Returns whether this call is a direct `TODO()` call.
     */
    private fun KtCallExpression.isTodoCall(): Boolean = calleeExpression?.text == TODO_CALL_NAME

    /**
     * Returns whether this class declaration is an interface.
     */
    private fun KtClass.isInterfaceClass(): Boolean = isInterface() || text.trimStart().startsWith(INTERFACE_PREFIX)

    /**
     * Returns whether this class declaration is an enum class.
     */
    private fun KtClass.isEnumClass(): Boolean =
        isEnum() || hasModifier(KtTokens.ENUM_KEYWORD) || text.trimStart().startsWith(ENUM_CLASS_PREFIX)

    /**
     * Returns whether this postfix expression is Kotlin's not-null assertion operator.
     */
    private fun KtPostfixExpression.isNotNullAssertion(): Boolean = operationReference.text == NOT_NULL_ASSERTION_OPERATOR

    /**
     * Returns whether any value argument contains a not-null assertion.
     */
    private fun KtCallExpression.hasNotNullArgument(): Boolean =
        valueArguments.any { argument ->
            val expression = argument.getArgumentExpression()
            if (expression is KtPostfixExpression && expression.isNotNullAssertion()) {
                return@any true
            }
            expression?.collectDescendantsOfType<KtPostfixExpression>()?.any { it.isNotNullAssertion() } == true
        }

    /**
     * Returns whether this type node is exactly `Any?`.
     */
    private fun KtNullableType.isAnyNullable(): Boolean = innerType?.text == ANY_TYPE_NAME

    /**
     * Returns whether this qualified expression is a Java interop leftover.
     */
    private fun KtDotQualifiedExpression.isJavaInteropReference(): Boolean {
        val receiver = receiverExpression.text
        val selector = selectorExpression?.text.orEmpty()
        val isClassForName = receiver == JAVA_LANG_CLASS_NAME && selector.startsWith(CLASS_FOR_NAME_SELECTOR)
        val isClassLiteral = selector == JAVA_CLASS_SELECTOR && receiver.endsWith(KOTLIN_CLASS_REFERENCE_SUFFIX)
        return receiver.endsWithAny(JAVA_INTEROP_QUALIFIER_SUFFIXES) || isClassForName || isClassLiteral
    }

    /**
     * Returns whether this call is a Java-style getter or setter invoked through a receiver.
     */
    private fun KtCallExpression.isGetterSetterCall(): Boolean {
        val callee = calleeExpression?.text ?: return false
        return parent.isQualifiedSelector(this) && callee.isGetterOrSetterName()
    }

    /**
     * Returns whether this parent uses [call] as a normal or safe-call selector.
     */
    private fun PsiElement?.isQualifiedSelector(call: KtCallExpression): Boolean =
        when (this) {
            is KtDotQualifiedExpression -> selectorExpression == call
            is KtSafeQualifiedExpression -> selectorExpression == call
            else -> false
        }

    /**
     * Returns whether this binary expression matches `nullable?.flag != true`.
     */
    private fun KtBinaryExpression.isNullableBooleanComparison(): Boolean =
        operationReference.text == NOT_EQUALS_OPERATOR &&
            right?.text == TRUE_LITERAL &&
            left?.containsSafeQualifiedExpression() == true

    /**
     * Returns whether this expression is or contains a safe-qualified expression.
     */
    private fun KtExpression.containsSafeQualifiedExpression(): Boolean =
        this is KtSafeQualifiedExpression || collectDescendantsOfType<KtSafeQualifiedExpression>().isNotEmpty()

    /**
     * Returns whether a `val` initializer eagerly reads from a getter-like expression.
     */
    private fun KtProperty.isEagerPropertyInitialization(): Boolean {
        if (isVar) {
            return false
        }
        val initializer = initializer ?: return false
        return initializer.isCallThenPropertyRead() || initializer.isGetterLikeCall()
    }

    /**
     * Returns whether this expression has the shape `buildValue().property`.
     */
    private fun KtExpression.isCallThenPropertyRead(): Boolean =
        this is KtDotQualifiedExpression && receiverExpression is KtCallExpression && selectorExpression !is KtCallExpression

    /**
     * Returns whether this expression is a getter-like call such as `getValue()` or `source.getValue()`.
     */
    private fun KtExpression.isGetterLikeCall(): Boolean =
        when (this) {
            is KtCallExpression -> calleeExpression?.text?.isGetterName() == true
            is KtDotQualifiedExpression -> (selectorExpression as? KtCallExpression)?.calleeExpression?.text?.isGetterName() == true
            else -> false
        }

    /**
     * Returns whether this method name is a Java-style getter or setter.
     */
    private fun String.isGetterOrSetterName(): Boolean = isGetterName() || isSetterName()

    /**
     * Returns whether this method name is a Java-style getter.
     */
    private fun String.isGetterName(): Boolean =
        startsWith(GETTER_PREFIX) && length > GETTER_PREFIX.length && this[GETTER_PREFIX.length].isUpperCase()

    /**
     * Returns whether this method name is a Java-style setter.
     */
    private fun String.isSetterName(): Boolean =
        startsWith(SETTER_PREFIX) && length > SETTER_PREFIX.length && this[SETTER_PREFIX.length].isUpperCase()

    /**
     * Returns whether this string contains any marker, ignoring case.
     */
    private fun String.containsAny(markers: Set<String>): Boolean = markers.any { contains(it, ignoreCase = true) }

    /**
     * Returns whether this string ends with any suffix.
     */
    private fun String.endsWithAny(suffixes: Set<String>): Boolean = suffixes.any { endsWith(it) }

    private companion object {
        private const val ANY_TYPE_NAME = "Any"
        private const val BOOLEAN_GETTER_PREFIX = "is"
        private const val CLASS_FOR_NAME_SELECTOR = "forName"
        private const val ENUM_CLASS_PREFIX = "enum class "
        private const val GETTER_PREFIX = "get"
        private const val INTERFACE_PREFIX = "interface "
        private const val JAVA_CLASS_SELECTOR = "java"
        private const val JAVA_LANG_CLASS_NAME = "Class"
        private const val KOTLIN_CLASS_REFERENCE_SUFFIX = "::class"
        private const val KOTLIN_SYNTHETIC_FILE_NAME = "Source.kt"
        private const val KOTLIN_SYNTHETIC_MODULE_NAME = "j2k-eval"
        private const val NOT_EQUALS_OPERATOR = "!="
        private const val NOT_NULL_ASSERTION_OPERATOR = "!!"
        private const val SETTER_PARAMETER_COUNT = 1
        private const val SETTER_PREFIX = "set"
        private const val TODO_CALL_NAME = "TODO"
        private const val TOP_LEVEL_OWNER = "<top-level>"
        private const val TRUE_LITERAL = "true"

        private val JAVA_INTEROP_QUALIFIER_SUFFIXES =
            setOf(
                "Arrays",
                "Collections",
                "Objects",
                "Optional",
            )
        private val UNRESOLVED_IMPORT_MARKERS = setOf("unresolved", "missing", "nonexistent")
    }
}

/**
 * Kotlin property name with its nearest containing type, if any.
 */
private data class KotlinPropertyRecord(
    val ownerName: String?,
    val name: String,
)

/**
 * JavaBean accessor mapping with its nearest containing Java type.
 */
private data class JavaBeanAccessorRecord(
    val ownerName: String,
    val methodName: String,
    val propertyNames: Set<String>,
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
    val publicApiNames: Set<String>,
) {
    /**
     * Count of class/interface/enum/object-like declarations.
     */
    val topLevelDeclarationCount: Int = classLikeCount + interfaceCount + enumCount + objectCount
}

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

/**
 * Internal counts for Kotlin quality warning patterns.
 */
private data class KotlinQualityCounts(
    val todoCount: Int,
    val notNullAssertionCount: Int,
    val notNullAssertionInCallCount: Int,
    val anyNullableCount: Int,
    val unresolvedImportCount: Int,
    val javaInteropReferenceCount: Int,
    val getterSetterCallCount: Int,
    val nullableBooleanComparisonCount: Int,
    val eagerPropertyInitializationCount: Int,
)
