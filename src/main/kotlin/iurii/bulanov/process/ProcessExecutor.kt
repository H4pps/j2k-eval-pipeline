package iurii.bulanov.process

import java.nio.file.Path

/**
 * Captured result of a spawned process.
 */
data class CommandResult(
    val command: String,
    val exitCode: Int,
    val output: String,
)

/**
 * Executes external commands used during benchmark checkout.
 */
class ProcessExecutor {
    /**
     * Executes a command with explicit argument tokens.
     */
    fun run(
        command: List<String>,
        workingDirectory: Path? = null,
    ): CommandResult {
        val builder = ProcessBuilder(command)
        if (workingDirectory != null) {
            builder.directory(workingDirectory.toFile())
        }
        builder.redirectErrorStream(true)

        val process = builder.start()
        val output =
            process.inputStream
                .bufferedReader()
                .readText()
                .trimEnd()
        val exitCode = process.waitFor()

        return CommandResult(command = command.joinToString(" "), exitCode = exitCode, output = output)
    }

    /**
     * Executes a shell command string via `bash -lc`.
     */
    fun runShell(
        command: String,
        workingDirectory: Path,
    ): CommandResult = run(listOf("bash", "-lc", command), workingDirectory)
}
