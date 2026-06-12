package iurii.bulanov.benchmark.evaluation.scanning

import com.github.javaparser.JavaParser
import com.github.javaparser.ParseStart
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.Providers
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.RecordDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.LiteralExpr
import com.github.javaparser.ast.expr.SwitchExpr
import com.github.javaparser.ast.stmt.DoStmt
import com.github.javaparser.ast.stmt.ForEachStmt
import com.github.javaparser.ast.stmt.ForStmt
import com.github.javaparser.ast.stmt.IfStmt
import com.github.javaparser.ast.stmt.ReturnStmt
import com.github.javaparser.ast.stmt.SwitchStmt
import com.github.javaparser.ast.stmt.ThrowStmt
import com.github.javaparser.ast.stmt.TryStmt
import com.github.javaparser.ast.stmt.WhileStmt
import iurii.bulanov.benchmark.evaluation.JavaBeanAccessorRecord
import iurii.bulanov.benchmark.evaluation.SourceContentProfile
import iurii.bulanov.benchmark.evaluation.SourceNullabilityProfile
import iurii.bulanov.benchmark.evaluation.SourceStructure

/**
 * Extracts Java structural, content, nullability, and JavaBean signals from source text.
 */
internal class JavaSourceScanner : SourceStructureScanner {
    private val javaParser = JavaParser(ParserConfiguration())

    /**
     * Scans Java source text.
     */
    override fun scan(source: String): SourceStructure {
        val unit = parseJava(source)
        val classes = unit.findAll(ClassOrInterfaceDeclaration::class.java)
        val classNames = classes.filterNot { it.isInterface }.map { it.nameAsString }.toSet()
        val interfaceNames = classes.filter { it.isInterface }.map { it.nameAsString }.toSet()
        val recordNames = unit.findAll(RecordDeclaration::class.java).map { it.nameAsString }.toSet()
        val enumNames = unit.findAll(EnumDeclaration::class.java).map { it.nameAsString }.toSet()
        val methodDeclarations = unit.findAll(MethodDeclaration::class.java)
        val methodNames = methodDeclarations.map { it.nameAsString }.toSet()
        val declarationNames = classNames + recordNames + interfaceNames + enumNames
        val javaBeanAccessorRecords = methodDeclarations.javaBeanAccessorRecords()
        val javaBeanAccessorPropertyNamesByOwner = methodDeclarations.javaBeanAccessorPropertyNamesByOwner()
        val content = unit.javaContentProfile(methodDeclarations)
        val nullability = unit.javaNullabilityProfile(methodDeclarations)

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
            kotlinPropertyRecords = emptyList(),
            javaBeanAccessorRecords = javaBeanAccessorRecords,
            content = content,
            nullability = nullability,
            publicApiNames = declarationNames + methodNames,
        )
    }

    private fun parseJava(source: String): CompilationUnit {
        val result = javaParser.parse(ParseStart.COMPILATION_UNIT, Providers.provider(source))
        return result.result.orElseThrow {
            IllegalArgumentException("Unable to parse Java source for evaluation: ${result.problems.joinToString()}")
        }
    }

    private fun CompilationUnit.javaContentProfile(methodDeclarations: List<MethodDeclaration>): SourceContentProfile =
        SourceContentProfile(
            nonEmptyFunctionNames =
                methodDeclarations
                    .filter { it.hasNonEmptyBody() }
                    .map { it.nameAsString }
                    .toSet(),
            emptyFunctionNames =
                methodDeclarations
                    .filter { it.hasExplicitEmptyBody() }
                    .map { it.nameAsString }
                    .toSet(),
            functionDeclarationCount = methodDeclarations.size,
            returnCount = findAll(ReturnStmt::class.java).size,
            branchCount =
                findAll(IfStmt::class.java).size +
                    findAll(SwitchStmt::class.java).size +
                    findAll(SwitchExpr::class.java).size,
            loopCount =
                findAll(ForStmt::class.java).size +
                    findAll(ForEachStmt::class.java).size +
                    findAll(WhileStmt::class.java).size +
                    findAll(DoStmt::class.java).size,
            throwCount = findAll(ThrowStmt::class.java).size,
            tryCount = findAll(TryStmt::class.java).size,
            literalValues = findAll(LiteralExpr::class.java).map { it.toString() }.toSet(),
        )

    private fun MethodDeclaration.hasNonEmptyBody(): Boolean = body.isPresent && body.get().statements.isNotEmpty()

    private fun MethodDeclaration.hasExplicitEmptyBody(): Boolean = body.isPresent && body.get().statements.isEmpty()

    private fun CompilationUnit.javaNullabilityProfile(methodDeclarations: List<MethodDeclaration>): SourceNullabilityProfile {
        val nullableNames = mutableSetOf<String>()
        val notNullNames = mutableSetOf<String>()
        var nullableCount = 0
        var notNullCount = 0

        fun record(
            name: String,
            annotations: List<AnnotationExpr>,
        ) {
            annotations.forEach { annotation ->
                when {
                    annotation.isNullableAnnotation() -> {
                        nullableNames += name
                        nullableCount += 1
                    }
                    annotation.isNotNullAnnotation() -> {
                        notNullNames += name
                        notNullCount += 1
                    }
                }
            }
        }

        findAll(FieldDeclaration::class.java).forEach { field ->
            field.variables.forEach { variable -> record(variable.nameAsString, field.annotations) }
        }
        methodDeclarations.forEach { method ->
            record(method.nameAsString, method.annotations)
            method.parameters.forEach { parameter -> record(parameter.nameAsString, parameter.annotations) }
        }
        findAll(RecordDeclaration::class.java).forEach { recordDeclaration ->
            recordDeclaration.parameters.forEach { parameter -> record(parameter.nameAsString, parameter.annotations) }
        }

        return SourceNullabilityProfile(
            nullableNames = nullableNames,
            notNullNames = notNullNames,
            nullableAnnotationCount = nullableCount,
            notNullAnnotationCount = notNullCount,
            nullableTypeNames = emptySet(),
            contradictoryNullabilityPatternCount = 0,
            nullComparisonCount = 0,
            nullabilityCastCount = 0,
            safeCallCount = 0,
            totalNullabilityOperationCount = 0,
        )
    }

    private fun List<MethodDeclaration>.javaBeanAccessorPropertyNamesByOwner(): Map<String, Map<String, Set<String>>> =
        javaBeanAccessorRecords()
            .groupBy { it.ownerName }
            .mapValues { (_, accessors) -> accessors.associate { it.methodName to it.propertyNames } }

    private fun List<MethodDeclaration>.javaBeanAccessorRecords(): List<JavaBeanAccessorRecord> =
        mapNotNull { method ->
            val ownerName = method.containingTypeName() ?: return@mapNotNull null
            val propertyNames = method.javaBeanAccessorPropertyNames().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            JavaBeanAccessorRecord(
                ownerName = ownerName,
                methodName = method.nameAsString,
                propertyNames = propertyNames,
                isPrivate = method.isPrivate,
                isStatic = method.isStatic,
            )
        }

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

    private fun Map<String, Map<String, Set<String>>>.flattenValues(): Map<String, Set<String>> =
        values
            .flatMap { it.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, propertySets) -> propertySets.flatten().toSet() }

    private fun MethodDeclaration.javaBeanAccessorPropertyNames(): Set<String> =
        when {
            nameAsString.startsWith(GETTER_PREFIX) && parameters.isEmpty() ->
                nameAsString
                    .removePrefix(GETTER_PREFIX)
                    .takeIf { it.hasBeanPropertyStem() }
                    ?.beanPropertyCandidates()
                    .orEmpty()
            nameAsString.startsWith(SETTER_PREFIX) && parameters.size == SETTER_PARAMETER_COUNT ->
                nameAsString
                    .removePrefix(SETTER_PREFIX)
                    .takeIf { it.hasBeanPropertyStem() }
                    ?.let { stem ->
                        stem.beanPropertyCandidates() +
                            if (parameters.single().type.asString() in BOOLEAN_TYPE_NAMES) {
                                setOf("$BOOLEAN_GETTER_PREFIX$stem")
                            } else {
                                emptySet()
                            }
                    }.orEmpty()
            nameAsString.startsWith(BOOLEAN_GETTER_PREFIX) && parameters.isEmpty() ->
                nameAsString
                    .removePrefix(BOOLEAN_GETTER_PREFIX)
                    .takeIf { it.hasBeanPropertyStem() }
                    ?.let { setOf(nameAsString, it.decapitalizeBeanProperty()) }
                    .orEmpty()
            else -> emptySet()
        }

    private fun String.beanPropertyCandidates(): Set<String> =
        buildSet {
            add(decapitalizeBeanProperty())
            if (all(Char::isUpperCase)) {
                add(lowercase())
            }
        }

    private fun String.hasBeanPropertyStem(): Boolean = isNotEmpty() && first().isUpperCase()

    private fun String.decapitalizeBeanProperty(): String =
        if (length > 1 && this[0].isUpperCase() && this[1].isUpperCase()) {
            this
        } else {
            replaceFirstChar { it.lowercaseChar() }
        }

    private fun AnnotationExpr.isNullableAnnotation(): Boolean = simpleAnnotationName() in NULLABLE_ANNOTATION_NAMES

    private fun AnnotationExpr.isNotNullAnnotation(): Boolean = simpleAnnotationName() in NOT_NULL_ANNOTATION_NAMES

    private fun AnnotationExpr.simpleAnnotationName(): String = nameAsString.substringAfterLast('.').lowercase()

    private companion object {
        private const val BOOLEAN_GETTER_PREFIX = "is"
        private const val GETTER_PREFIX = "get"
        private const val SETTER_PARAMETER_COUNT = 1
        private const val SETTER_PREFIX = "set"

        private val BOOLEAN_TYPE_NAMES = setOf("Boolean", "boolean", "java.lang.Boolean")
        private val NOT_NULL_ANNOTATION_NAMES = setOf("nonnull", "notnull")
        private val NULLABLE_ANNOTATION_NAMES = setOf("checkfornull", "nullable")
    }
}
