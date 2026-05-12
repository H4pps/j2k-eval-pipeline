package iurii.bulanov.benchmark.evaluation

/**
 * Extracts deterministic structural and quality signals from source text.
 */
class SourceTextScanner {
    /**
     * Scans Java source text for package, declarations, and method-like members.
     */
    fun scanJava(source: String): SourceStructure {
        val cleaned = stripCommentsAndStrings(source)
        val declarations = JAVA_DECLARATION_REGEX.findAll(cleaned).map { match -> match.groupValues[2] to match.groupValues[1] }.toList()
        val methodNames =
            JAVA_METHOD_REGEX
                .findAll(cleaned)
                .map { match -> match.groupValues[1] }
                .filterNot { it in CONTROL_KEYWORDS }
                .toSet()
        val declarationNames = declarations.map { it.first }.toSet()
        return SourceStructure(
            packageName = packageName(cleaned, JAVA_PACKAGE_REGEX),
            classLikeCount = declarations.count { it.second == "class" || it.second == "record" },
            interfaceCount = declarations.count { it.second == "interface" },
            enumCount = declarations.count { it.second == "enum" },
            objectCount = 0,
            topLevelNames = declarationNames,
            functionNames = methodNames,
            publicApiNames = declarationNames + methodNames,
        )
    }

    /**
     * Scans Kotlin source text for package, declarations, and function members.
     */
    fun scanKotlin(source: String): SourceStructure {
        val cleaned = stripCommentsAndStrings(source)
        val declarations = KOTLIN_DECLARATION_REGEX.findAll(cleaned).map { match -> match.groupValues[2] to match.groupValues[1] }.toList()
        val functionNames = KOTLIN_FUNCTION_REGEX.findAll(cleaned).map { it.groupValues[1] }.toSet()
        val declarationNames = declarations.map { it.first }.toSet()
        return SourceStructure(
            packageName = packageName(cleaned, KOTLIN_PACKAGE_REGEX),
            classLikeCount = declarations.count { it.second in KOTLIN_CLASS_DECLARATIONS },
            interfaceCount = declarations.count { it.second == "interface" },
            enumCount = declarations.count { it.second == "enum class" },
            objectCount = declarations.count { it.second == "object" },
            topLevelNames = declarationNames,
            functionNames = functionNames,
            publicApiNames = declarationNames + functionNames,
        )
    }

    /**
     * Scans Kotlin source text for mechanical conversion quality warnings.
     */
    fun scanKotlinQuality(
        path: String,
        source: String,
    ): QualityFileMetrics {
        val cleaned = stripCommentsAndStrings(source)
        val counts = qualityCounts(cleaned)
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
     * Replaces comments and string/character literals with whitespace.
     */
    fun stripCommentsAndStrings(source: String): String {
        val output = StringBuilder(source.length)
        var index = 0
        while (index < source.length) {
            when {
                source.startsWith("//", index) -> index = consumeLineComment(source, index, output)
                source.startsWith("/*", index) -> index = consumeBlockComment(source, index, output)
                source[index] == '"' -> index = consumeQuoted(source, index, output, '"')
                source[index] == '\'' -> index = consumeQuoted(source, index, output, '\'')
                else -> output.append(source[index++])
            }
        }
        return output.toString()
    }

    /**
     * Counts all Kotlin quality warning patterns in cleaned source text.
     */
    private fun qualityCounts(source: String): KotlinQualityCounts =
        KotlinQualityCounts(
            todoCount = TODO_REGEX.findAll(source).count(),
            notNullAssertionCount = NOT_NULL_ASSERTION_REGEX.findAll(source).count(),
            notNullAssertionInCallCount = NOT_NULL_IN_CALL_REGEX.findAll(source).count(),
            anyNullableCount = ANY_NULLABLE_REGEX.findAll(source).count(),
            unresolvedImportCount = UNRESOLVED_IMPORT_REGEX.findAll(source).count(),
            javaInteropReferenceCount = JAVA_INTEROP_REGEX.findAll(source).count(),
            getterSetterCallCount = GETTER_SETTER_CALL_REGEX.findAll(source).count(),
            nullableBooleanComparisonCount = NULLABLE_BOOLEAN_REGEX.findAll(source).count(),
            eagerPropertyInitializationCount = EAGER_PROPERTY_REGEX.findAll(source).count(),
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
     * Extracts package declaration using [regex].
     */
    private fun packageName(
        source: String,
        regex: Regex,
    ): String? = regex.find(source)?.groupValues?.get(1)

    /**
     * Consumes a line comment while preserving line boundaries.
     */
    private fun consumeLineComment(
        source: String,
        start: Int,
        output: StringBuilder,
    ): Int {
        var index = start
        while (index < source.length && source[index] != '\n') {
            output.append(' ')
            index++
        }
        return index
    }

    /**
     * Consumes a block comment while preserving line boundaries.
     */
    private fun consumeBlockComment(
        source: String,
        start: Int,
        output: StringBuilder,
    ): Int {
        var index = start
        while (index < source.length) {
            val character = source[index]
            output.append(if (character == '\n') '\n' else ' ')
            if (source.startsWith("*/", index)) {
                output.append(' ')
                return index + 2
            }
            index++
        }
        return index
    }

    /**
     * Consumes a quoted literal while preserving line boundaries.
     */
    private fun consumeQuoted(
        source: String,
        start: Int,
        output: StringBuilder,
        quote: Char,
    ): Int {
        var index = start
        output.append(' ')
        index++
        var escaped = false
        while (index < source.length) {
            val character = source[index]
            output.append(if (character == '\n') '\n' else ' ')
            if (!escaped && character == quote) {
                return index + 1
            }
            escaped = !escaped && character == '\\'
            if (escaped && character != '\\') {
                escaped = false
            }
            index++
        }
        return index
    }

    private companion object {
        private val JAVA_PACKAGE_REGEX = Regex("""(?m)^\s*package\s+([A-Za-z_][\w.]*)\s*;""")
        private val KOTLIN_PACKAGE_REGEX = Regex("""(?m)^\s*package\s+([A-Za-z_][\w.]*)""")
        private val JAVA_DECLARATION_REGEX = Regex("""\b(class|interface|enum|record)\s+([A-Za-z_]\w*)""")
        private val JAVA_METHOD_REGEX =
            Regex(
                """\b(?:public|protected|private|static|final|synchronized|abstract|native|\s)+[A-Za-z_][\w<>\[\],.? ]+\s+([A-Za-z_]\w*)\s*\([^;{}]*\)\s*(?:throws\s+[^{]+)?\{""",
            )
        private val KOTLIN_DECLARATION_REGEX =
            Regex("""\b(enum\s+class|data\s+class|sealed\s+class|class|interface|object)\s+([A-Za-z_]\w*)""")
        private val KOTLIN_CLASS_DECLARATIONS = setOf("class", "data class", "sealed class")
        private val KOTLIN_FUNCTION_REGEX = Regex("""\bfun\s+([A-Za-z_]\w*)\s*\(""")
        private val TODO_REGEX = Regex("""\bTODO\s*\(""")
        private val NOT_NULL_ASSERTION_REGEX = Regex("""!!""")
        private val NOT_NULL_IN_CALL_REGEX = Regex("""\b[A-Za-z_]\w*\s*\([^)]*!![^)]*\)""")
        private val ANY_NULLABLE_REGEX = Regex("""\bAny\?""")
        private val UNRESOLVED_IMPORT_REGEX =
            Regex("""(?m)^\s*import\s+.*(?:unresolved|missing|nonexistent).*""", RegexOption.IGNORE_CASE)
        private val JAVA_INTEROP_REGEX =
            Regex("""\b(?:Arrays\.|Collections\.|Objects\.|Optional[.<]|Class\.forName|::class\.java|\.class\b)""")
        private val GETTER_SETTER_CALL_REGEX = Regex("""\.\s*(?:get|set)[A-Z]\w*\s*\(""")
        private val NULLABLE_BOOLEAN_REGEX = Regex("""\?\.[A-Za-z_]\w*\s*!=\s*true""")
        private val EAGER_PROPERTY_REGEX =
            Regex(
                """(?m)^\s*val\s+[A-Za-z_]\w*(?:\s*:[^=]+)?\s*=\s*(?:[A-Za-z_]\w*\([^)]*\)\.[A-Za-z_]\w*|(?:[A-Za-z_]\w*\.)?get[A-Z]\w*\()""",
            )
        private val CONTROL_KEYWORDS = setOf("if", "for", "while", "switch", "catch", "return", "new")
    }
}

/**
 * Structural data extracted from one source file.
 */
data class SourceStructure(
    val packageName: String?,
    val classLikeCount: Int,
    val interfaceCount: Int,
    val enumCount: Int,
    val objectCount: Int,
    val topLevelNames: Set<String>,
    val functionNames: Set<String>,
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
