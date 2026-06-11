package iurii.bulanov.benchmark.evaluation.scanning

import iurii.bulanov.benchmark.evaluation.KotlinPropertyRecord
import iurii.bulanov.benchmark.evaluation.SourceContentProfile
import iurii.bulanov.benchmark.evaluation.SourceNullabilityProfile
import iurii.bulanov.benchmark.evaluation.SourceStructure
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Extracts Kotlin structural, content, and nullability signals from PSI.
 */
internal class KotlinSourceScanner(
    private val parser: KotlinPsiParser,
) : SourceStructureScanner {
    /**
     * Scans Kotlin source text.
     */
    override fun scan(source: String): SourceStructure {
        val file = parser.parse(source)
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
        val publicPropertyRecords = propertyRecords.filterNot { it.isPrivate }
        val propertyNames = publicPropertyRecords.map { it.name }.toSet()
        val declarationNames = classNames + interfaceNames + enumNames + objectNames
        val content = file.kotlinContentProfile()
        val nullability = file.kotlinNullabilityProfile()

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
            propertyNamesByOwner = publicPropertyRecords.groupByOwner(),
            javaBeanAccessorPropertyNames = emptyMap(),
            javaBeanAccessorPropertyNamesByOwner = emptyMap(),
            kotlinPropertyRecords = propertyRecords,
            javaBeanAccessorRecords = emptyList(),
            content = content,
            nullability = nullability,
            publicApiNames = declarationNames + functionNames + propertyNames,
        )
    }

    private fun KtFile.kotlinPropertyRecords(): List<KotlinPropertyRecord> {
        val declaredProperties =
            collectDescendantsOfType<KtProperty>()
                .filter { it.parent is KtFile || it.parent is KtClassBody }
                .mapNotNull { property ->
                    property.name?.let {
                        KotlinPropertyRecord(
                            ownerName = property.containingTypeName(),
                            name = it,
                            isPrivate = property.hasModifier(KtTokens.PRIVATE_KEYWORD),
                        )
                    }
                }
        val constructorProperties =
            collectDescendantsOfType<KtParameter>()
                .filter { it.hasValOrVar() }
                .mapNotNull { parameter ->
                    parameter.name?.let {
                        KotlinPropertyRecord(
                            ownerName = parameter.containingTypeName(),
                            name = it,
                            isPrivate = parameter.hasModifier(KtTokens.PRIVATE_KEYWORD),
                        )
                    }
                }
        return declaredProperties + constructorProperties
    }

    private fun List<KotlinPropertyRecord>.groupByOwner(): Map<String, Set<String>> =
        groupBy { it.ownerName ?: TOP_LEVEL_OWNER }
            .mapValues { (_, properties) -> properties.map { it.name }.toSet() }

    private fun KtFile.kotlinContentProfile(): SourceContentProfile {
        val functions = collectDescendantsOfType<KtNamedFunction>()
        return SourceContentProfile(
            nonEmptyFunctionNames =
                functions
                    .filter { it.hasNonEmptyBody() }
                    .mapNotNull { it.name }
                    .toSet(),
            emptyFunctionNames =
                functions
                    .filter { it.hasExplicitEmptyBody() }
                    .mapNotNull { it.name }
                    .toSet(),
            functionDeclarationCount = functions.size,
            returnCount = collectDescendantsOfType<KtReturnExpression>().size,
            branchCount = collectDescendantsOfType<KtIfExpression>().size + collectDescendantsOfType<KtWhenExpression>().size,
            loopCount = collectDescendantsOfType<KtLoopExpression>().size,
            throwCount = collectDescendantsOfType<KtThrowExpression>().size,
            tryCount = collectDescendantsOfType<KtTryExpression>().size,
            literalValues =
                (
                    collectDescendantsOfType<KtConstantExpression>().map { it.text } +
                        collectDescendantsOfType<KtStringTemplateExpression>().map { it.text }
                ).toSet(),
        )
    }

    private fun KtFile.kotlinNullabilityProfile(): SourceNullabilityProfile {
        val nullComparisonCount = collectDescendantsOfType<KtBinaryExpression>().count { it.isNullComparison() }
        val castCount = collectDescendantsOfType<KtBinaryExpressionWithTypeRHS>().count { it.isNullabilityCast() }
        val safeCallCount = collectDescendantsOfType<KtSafeQualifiedExpression>().size
        return SourceNullabilityProfile(
            nullableNames = emptySet(),
            notNullNames = emptySet(),
            nullableAnnotationCount = 0,
            notNullAnnotationCount = 0,
            nullableTypeNames =
                (
                    collectDescendantsOfType<KtNamedFunction>()
                        .filter { it.typeReference?.typeElement is KtNullableType }
                        .mapNotNull { it.name } +
                        collectDescendantsOfType<KtProperty>()
                            .filter { it.typeReference?.typeElement is KtNullableType }
                            .mapNotNull { it.name } +
                        collectDescendantsOfType<KtParameter>()
                            .filter { it.typeReference?.typeElement is KtNullableType }
                            .mapNotNull { it.name }
                ).toSet(),
            contradictoryNullabilityPatternCount = contradictoryNullabilityPatternCount(),
            nullComparisonCount = nullComparisonCount,
            nullabilityCastCount = castCount,
            safeCallCount = safeCallCount,
            totalNullabilityOperationCount = nullComparisonCount + castCount + safeCallCount,
        )
    }

    private fun KtFile.contradictoryNullabilityPatternCount(): Int =
        collectDescendantsOfType<KtNamedFunction>().sumOf { function ->
            function.contradictoryNullabilityPatternCount()
        }

    private fun KtNamedFunction.contradictoryNullabilityPatternCount(): Int {
        val nullChecks = collectDescendantsOfType<KtBinaryExpression>().filter { it.isNullComparison() }
        val propertyContradictions =
            collectDescendantsOfType<KtProperty>().count { property ->
                val name = property.name ?: return@count false
                val cast = property.initializer as? KtBinaryExpressionWithTypeRHS ?: return@count false
                cast.isNonNullCast() && nullChecks.hasNearbyCheckFor(name, property.lineNumber())
            }
        val assignmentContradictions =
            collectDescendantsOfType<KtBinaryExpression>().count { expression ->
                val name = expression.left?.text ?: return@count false
                val cast = expression.right as? KtBinaryExpressionWithTypeRHS ?: return@count false
                expression.operationReference.text == ASSIGNMENT_OPERATOR &&
                    name.isIdentifierLike() &&
                    cast.isNonNullCast() &&
                    nullChecks.hasNearbyCheckFor(name, expression.lineNumber())
            }
        return propertyContradictions + assignmentContradictions
    }

    private fun List<KtBinaryExpression>.hasNearbyCheckFor(
        name: String,
        line: Int,
    ): Boolean =
        any { check ->
            val checkLine = check.lineNumber()
            checkLine > line &&
                checkLine - line <= CONTRADICTORY_NULLABILITY_LINE_WINDOW &&
                check.referencesNameInNullComparison(name)
        }

    private fun KtBinaryExpression.referencesNameInNullComparison(name: String): Boolean =
        (left?.text == name && right?.text == NULL_LITERAL) ||
            (right?.text == name && left?.text == NULL_LITERAL)

    private fun KtBinaryExpression.isNullComparison(): Boolean =
        operationReference.text in NULL_COMPARISON_OPERATORS &&
            (left?.text == NULL_LITERAL || right?.text == NULL_LITERAL)

    private fun KtBinaryExpressionWithTypeRHS.isNullabilityCast(): Boolean = operationReference.text in CAST_OPERATORS

    private fun KtBinaryExpressionWithTypeRHS.isNonNullCast(): Boolean =
        operationReference.text == NON_NULL_CAST_OPERATOR &&
            right?.typeElement !is KtNullableType &&
            right?.text?.trim()?.endsWith(NULLABLE_TYPE_SUFFIX) == false

    private fun PsiElement.lineNumber(): Int = containingFile.text.take(textOffset).count { it == '\n' } + 1

    private fun String.isIdentifierLike(): Boolean = matches(IDENTIFIER_REGEX)

    private fun KtNamedFunction.hasNonEmptyBody(): Boolean {
        val body = bodyExpression ?: return false
        return body.text.trim() != EMPTY_BLOCK
    }

    private fun KtNamedFunction.hasExplicitEmptyBody(): Boolean = bodyExpression?.text?.trim() == EMPTY_BLOCK

    private fun KtElement.containingTypeName(): String? {
        var current = parent
        while (current != null) {
            when (current) {
                is KtClass -> return current.name
                is KtObjectDeclaration -> return if (current.isCompanion()) current.containingClassName() else current.name
            }
            current = current.parent
        }
        return null
    }

    private fun KtObjectDeclaration.containingClassName(): String? {
        var current = parent
        while (current != null) {
            if (current is KtClass) {
                return current.name
            }
            current = current.parent
        }
        return null
    }

    private fun KtClass.isInterfaceClass(): Boolean = isInterface() || text.trimStart().startsWith(INTERFACE_PREFIX)

    private fun KtClass.isEnumClass(): Boolean =
        isEnum() || hasModifier(KtTokens.ENUM_KEYWORD) || text.trimStart().startsWith(ENUM_CLASS_PREFIX)

    private companion object {
        private const val ASSIGNMENT_OPERATOR = "="
        private const val CONTRADICTORY_NULLABILITY_LINE_WINDOW = 10
        private const val EMPTY_BLOCK = "{}"
        private const val ENUM_CLASS_PREFIX = "enum class "
        private const val INTERFACE_PREFIX = "interface "
        private const val NON_NULL_CAST_OPERATOR = "as"
        private const val NULL_LITERAL = "null"
        private const val NULLABLE_TYPE_SUFFIX = "?"
        private const val TOP_LEVEL_OWNER = "<top-level>"

        private val CAST_OPERATORS = setOf("as", "as?")
        private val IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private val NULL_COMPARISON_OPERATORS = setOf("==", "!=")
    }
}
