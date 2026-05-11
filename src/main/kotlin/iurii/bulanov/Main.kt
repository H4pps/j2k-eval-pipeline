package iurii.bulanov

import iurii.bulanov.benchmark.checkout.BenchmarkCheckoutRunner
import iurii.bulanov.cli.BenchmarkCheckoutCli
import iurii.bulanov.logging.JsonLineLogger
import kotlin.system.exitProcess

/**
 * Entry point for benchmark checkout CLI commands.
 */
fun main(args: Array<String>) {
    val logger = JsonLineLogger()
    val runner = BenchmarkCheckoutRunner(logger = logger)
    val exitCode = BenchmarkCheckoutCli(logger = logger, runner = runner).run(args.toList())
    if (exitCode != 0) {
        exitProcess(exitCode)
    }
}
