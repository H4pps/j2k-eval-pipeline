package iurii.bulanov.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import iurii.bulanov.benchmark.checkout.BenchmarkCheckoutRunner
import iurii.bulanov.benchmark.checkout.CheckoutBenchmarkRequest
import iurii.bulanov.benchmark.evaluation.EvaluationRequest
import iurii.bulanov.benchmark.evaluation.EvaluatorRunner
import iurii.bulanov.logging.JsonLineLogger
import iurii.bulanov.logging.StructuredLogger

/**
 * CLI entrypoint facade for all J2K evaluation harness commands.
 *
 * Structured logging is emitted through an injected [logger].
 */
class J2kEvalCliApplication(
    private val logger: StructuredLogger = JsonLineLogger(),
    private val checkoutRunner: BenchmarkCheckoutRunner = BenchmarkCheckoutRunner(logger = logger),
    private val evaluatorRunner: EvaluatorRunner = EvaluatorRunner(logger = logger),
) {
    /**
     * Parses [args], executes the selected command, and returns a process-style exit code.
     */
    fun run(args: List<String>): Int {
        val command = J2kEvalCli(checkoutRunner = checkoutRunner, evaluatorRunner = evaluatorRunner)
        return try {
            command.parse(args)
            0
        } catch (result: ProgramResult) {
            result.statusCode
        } catch (error: CliktError) {
            command.echoFormattedHelp(error)
            error.statusCode
        } catch (exception: Exception) {
            logger.error(
                "j2k_eval_cli_failed",
                mapOf("error" to (exception.message ?: exception::class.simpleName)),
            )
            1
        }
    }
}

/**
 * Root Clikt command for the J2K evaluation harness CLI.
 */
class J2kEvalCli(
    private val checkoutRunner: BenchmarkCheckoutRunner = BenchmarkCheckoutRunner(),
    private val evaluatorRunner: EvaluatorRunner = EvaluatorRunner(),
) : CliktCommand(name = "j2k-eval") {
    init {
        subcommands(
            CheckoutBenchmarkCliktCommand(checkoutRunner),
            EvaluateCliktCommand(evaluatorRunner),
        )
    }

    /**
     * Root command has no direct action and only hosts subcommands.
     */
    override fun run() = Unit
}

/**
 * Clikt subcommand that validates benchmark checkout inputs and runtime sanity checks.
 */
private class CheckoutBenchmarkCliktCommand(
    private val runner: BenchmarkCheckoutRunner,
) : CliktCommand(name = "checkout-benchmark") {
    private val configPath by option("--config", help = "Path to benchmark YAML config").path().required()
    private val runBuild by option("--run-build", help = "Run configured benchmark build commands").flag(default = false)
    private val githubSummaryPath by option("--github-summary", help = "Path to GitHub step summary output").path()
    private val githubOutputPath by option("--github-output", help = "Path to GitHub output file").path()
    private val checkoutReportPath by option("--checkout-report", help = "Path to checkout JSON report").path()

    /**
     * Executes the benchmark checkout workflow through [BenchmarkCheckoutRunner].
     */
    override fun run() {
        val exitCode =
            runner.run(
                CheckoutBenchmarkRequest(
                    configPath = configPath,
                    runBuild = runBuild,
                    githubSummaryPath = githubSummaryPath,
                    githubOutputPath = githubOutputPath,
                    checkoutReportPath = checkoutReportPath,
                ),
            )
        if (exitCode != 0) {
            throw ProgramResult(exitCode)
        }
    }
}

/**
 * Clikt subcommand that runs the Kotlin evaluator skeleton.
 */
private class EvaluateCliktCommand(
    private val runner: EvaluatorRunner,
) : CliktCommand(name = "evaluate") {
    private val configPath by option("--config", help = "Path to benchmark YAML config").path().required()
    private val generatedKotlinPath by option("--generated-kotlin", help = "Generated Kotlin output directory").path()
    private val reportDirectoryPath by option("--report-dir", help = "Evaluator report output directory").path()
    private val conversionReportPath by option("--conversion-report", help = "J2K conversion JSON report path").path()
    private val checkoutReportPath by option("--checkout-report", help = "Benchmark checkout JSON report path").path()
    private val githubSummaryPath by option("--github-summary", help = "Path to GitHub step summary output").path()

    /**
     * Executes evaluator metric calculation and report generation through [EvaluatorRunner].
     */
    override fun run() {
        val exitCode =
            runner.run(
                EvaluationRequest(
                    configPath = configPath,
                    generatedKotlinDirectory = generatedKotlinPath,
                    reportDirectory = reportDirectoryPath,
                    conversionReport = conversionReportPath,
                    checkoutReport = checkoutReportPath,
                    githubSummaryPath = githubSummaryPath,
                ),
            )
        if (exitCode != 0) {
            throw ProgramResult(exitCode)
        }
    }
}
