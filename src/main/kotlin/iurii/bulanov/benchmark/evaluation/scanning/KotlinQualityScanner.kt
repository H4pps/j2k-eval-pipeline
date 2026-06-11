package iurii.bulanov.benchmark.evaluation.scanning

import iurii.bulanov.benchmark.evaluation.EvaluationWarning
import iurii.bulanov.benchmark.evaluation.QualityFileMetrics
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Extracts mechanical conversion quality warnings from Kotlin PSI.
 */
internal class KotlinQualityScanner(
    private val parser: KotlinPsiParser,
) : SourceQualityScanner {
    /**
     * Scans Kotlin source text for quality warning counts and findings.
     */
    override fun scan(
        path: String,
        source: String,
    ): QualityFileMetrics {
        val file = parser.parse(source)
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

    private fun KtCallExpression.isTodoCall(): Boolean = calleeExpression?.text == TODO_CALL_NAME

    private fun KtPostfixExpression.isNotNullAssertion(): Boolean = operationReference.text == NOT_NULL_ASSERTION_OPERATOR

    private fun KtCallExpression.hasNotNullArgument(): Boolean =
        valueArguments.any { argument ->
            val expression = argument.getArgumentExpression()
            if (expression is KtPostfixExpression && expression.isNotNullAssertion()) {
                return@any true
            }
            expression?.collectDescendantsOfType<KtPostfixExpression>()?.any { it.isNotNullAssertion() } == true
        }

    private fun KtNullableType.isAnyNullable(): Boolean = innerType?.text == ANY_TYPE_NAME

    private fun KtDotQualifiedExpression.isJavaInteropReference(): Boolean {
        val receiver = receiverExpression.text
        val selector = selectorExpression?.text.orEmpty()
        val isClassForName = receiver == JAVA_LANG_CLASS_NAME && selector.startsWith(CLASS_FOR_NAME_SELECTOR)
        val isClassLiteral = selector == JAVA_CLASS_SELECTOR && receiver.endsWith(KOTLIN_CLASS_REFERENCE_SUFFIX)
        return receiver.endsWithAny(JAVA_INTEROP_QUALIFIER_SUFFIXES) || isClassForName || isClassLiteral
    }

    private fun KtCallExpression.isGetterSetterCall(): Boolean {
        val callee = calleeExpression?.text ?: return false
        return parent.isQualifiedSelector(this) && callee.isGetterOrSetterName()
    }

    private fun PsiElement?.isQualifiedSelector(call: KtCallExpression): Boolean =
        when (this) {
            is KtDotQualifiedExpression -> selectorExpression == call
            is KtSafeQualifiedExpression -> selectorExpression == call
            else -> false
        }

    private fun KtBinaryExpression.isNullableBooleanComparison(): Boolean =
        operationReference.text == NOT_EQUALS_OPERATOR &&
            right?.text == TRUE_LITERAL &&
            left?.containsSafeQualifiedExpression() == true

    private fun KtExpression.containsSafeQualifiedExpression(): Boolean =
        this is KtSafeQualifiedExpression || collectDescendantsOfType<KtSafeQualifiedExpression>().isNotEmpty()

    private fun KtProperty.isEagerPropertyInitialization(): Boolean {
        if (isVar) {
            return false
        }
        val initializer = initializer ?: return false
        return initializer.isCallThenPropertyRead() || initializer.isGetterLikeCall()
    }

    private fun KtExpression.isCallThenPropertyRead(): Boolean =
        this is KtDotQualifiedExpression && receiverExpression is KtCallExpression && selectorExpression !is KtCallExpression

    private fun KtExpression.isGetterLikeCall(): Boolean =
        when (this) {
            is KtCallExpression -> calleeExpression?.text?.isGetterName() == true
            is KtDotQualifiedExpression -> (selectorExpression as? KtCallExpression)?.calleeExpression?.text?.isGetterName() == true
            else -> false
        }

    private fun String.isGetterOrSetterName(): Boolean = isGetterName() || isSetterName()

    private fun String.isGetterName(): Boolean =
        startsWith(GETTER_PREFIX) && length > GETTER_PREFIX.length && this[GETTER_PREFIX.length].isUpperCase()

    private fun String.isSetterName(): Boolean =
        startsWith(SETTER_PREFIX) && length > SETTER_PREFIX.length && this[SETTER_PREFIX.length].isUpperCase()

    private fun String.containsAny(markers: Set<String>): Boolean = markers.any { contains(it, ignoreCase = true) }

    private fun String.endsWithAny(suffixes: Set<String>): Boolean = suffixes.any { endsWith(it) }

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

    private companion object {
        private const val ANY_TYPE_NAME = "Any"
        private const val CLASS_FOR_NAME_SELECTOR = "forName"
        private const val GETTER_PREFIX = "get"
        private const val JAVA_CLASS_SELECTOR = "java"
        private const val JAVA_LANG_CLASS_NAME = "Class"
        private const val KOTLIN_CLASS_REFERENCE_SUFFIX = "::class"
        private const val NOT_EQUALS_OPERATOR = "!="
        private const val NOT_NULL_ASSERTION_OPERATOR = "!!"
        private const val SETTER_PREFIX = "set"
        private const val TODO_CALL_NAME = "TODO"
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
