package iurii.bulanov.benchmark.config

import iurii.bulanov.benchmark.checkout.CheckoutException
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Parses and validates benchmark YAML configuration files.
 */
class BenchmarkConfigParser {
    private val yamlLoad =
        Load(
            LoadSettings
                .builder()
                .setAllowDuplicateKeys(false)
                .build(),
        )

    /**
     * Parses a benchmark YAML file into a strongly typed [BenchmarkConfig].
     */
    fun parse(configPath: Path): BenchmarkConfig {
        val parsedRoot =
            Files.newInputStream(configPath).use { input ->
                yamlLoad.loadFromInputStream(input)
            }
        val root = parsedRoot as? Map<*, *> ?: throw CheckoutException("benchmark config must be a mapping: $configPath")

        val id = requiredString(root, listOf("id"))
        val name = requiredString(root, listOf("name"))
        val role = requiredString(root, listOf("role"))
        val description = requiredString(root, listOf("description"))

        val repository =
            RepositoryConfig(
                upstream = requiredString(root, listOf("repository", "upstream")),
                source = requiredString(root, listOf("repository", "source")),
                ref = requiredString(root, listOf("repository", "ref")),
                branch = requiredString(root, listOf("repository", "branch")),
            )

        val checkoutDirectory = requiredString(root, listOf("checkout", "directory"))
        CheckoutDirectoryPolicy.validate(checkoutDirectory)

        val javaSourceRoots = requiredNonEmptyStringList(root, listOf("java", "sourceRoots"), "java.sourceRoots")
        val buildCommands = requiredNonEmptyStringList(root, listOf("build", "commands"), "build.commands")

        return BenchmarkConfig(
            id = id,
            name = name,
            role = role,
            description = description,
            repository = repository,
            checkout = CheckoutConfig(directory = checkoutDirectory),
            java = JavaConfig(sourceRoots = javaSourceRoots),
            build =
                BuildConfig(
                    tool = requiredString(root, listOf("build", "tool")),
                    workingDirectory = requiredString(root, listOf("build", "workingDirectory")),
                    commands = buildCommands,
                ),
        )
    }

    /**
     * Fetches a required field by path and validates it as a non-empty string.
     */
    private fun requiredString(
        root: Map<*, *>,
        path: List<String>,
    ): String {
        val value = requiredValue(root, path)
        if (value !is String || value.isBlank()) {
            throw CheckoutException("${path.joinToString(".")} must be a non-empty string")
        }
        return value
    }

    /**
     * Fetches a required field by path and validates it as a non-empty list of strings.
     */
    private fun requiredNonEmptyStringList(
        root: Map<*, *>,
        path: List<String>,
        fieldName: String,
    ): List<String> {
        val value = requiredValue(root, path)
        val list = value as? List<*> ?: throw CheckoutException("$fieldName must be a non-empty list")
        if (list.isEmpty()) {
            throw CheckoutException("$fieldName must be a non-empty list")
        }
        return list.mapIndexed { index, item ->
            val asString = item as? String ?: throw CheckoutException("$fieldName[$index] must be a non-empty string")
            if (asString.isBlank()) {
                throw CheckoutException("$fieldName[$index] must be a non-empty string")
            }
            asString
        }
    }

    /**
     * Traverses a nested map and returns the required value at [path].
     */
    private fun requiredValue(
        root: Map<*, *>,
        path: List<String>,
    ): Any? {
        var current: Any? = root
        path.forEachIndexed { index, key ->
            val currentMap =
                current as? Map<*, *> ?: throw CheckoutException("missing required config key: ${path.take(index + 1).joinToString(".")}")
            if (!currentMap.containsKey(key)) {
                throw CheckoutException("missing required config key: ${path.joinToString(".")}")
            }
            current = currentMap[key]
        }
        return current ?: throw CheckoutException("missing required config key: ${path.joinToString(".")}")
    }
}

/**
 * Enforces checkout directory safety constraints.
 */
object CheckoutDirectoryPolicy {
    private const val CHECKOUT_PREFIX = "build/benchmarks/"

    /**
     * Validates a checkout directory path used by benchmark cloning.
     */
    fun validate(checkoutDirectory: String) {
        val path = Paths.get(checkoutDirectory)
        if (path.isAbsolute) {
            throw CheckoutException("checkout.directory must be a relative path: $checkoutDirectory")
        }
        if (checkoutDirectory.contains("..")) {
            throw CheckoutException("checkout.directory must not contain '..': $checkoutDirectory")
        }
        val normalized = path.normalize().toString().replace('\\', '/')
        if (!normalized.startsWith(CHECKOUT_PREFIX)) {
            throw CheckoutException("checkout.directory must start with $CHECKOUT_PREFIX: $checkoutDirectory")
        }
    }
}
