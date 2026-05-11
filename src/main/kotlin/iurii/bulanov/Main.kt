package iurii.bulanov

import iurii.bulanov.benchmark.checkout.BenchmarkCheckoutRunner
import iurii.bulanov.benchmark.evaluation.EvaluatorRunner
import iurii.bulanov.cli.J2kEvalCliApplication
import iurii.bulanov.logging.JsonLineLogger
import kotlin.system.exitProcess

/**
 * Entry point for J2K evaluation harness CLI commands.
 */
fun main(args: Array<String>) {
    val logger = JsonLineLogger()
    val checkoutRunner = BenchmarkCheckoutRunner(logger = logger)
    val evaluatorRunner = EvaluatorRunner(logger = logger)
    val exitCode =
        J2kEvalCliApplication(
            logger = logger,
            checkoutRunner = checkoutRunner,
            evaluatorRunner = evaluatorRunner,
        ).run(args.toList())
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}
