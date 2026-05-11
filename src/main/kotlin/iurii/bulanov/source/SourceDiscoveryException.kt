package iurii.bulanov.source

/**
 * Signals validation or discovery failures when resolving Java or Kotlin source files.
 */
class SourceDiscoveryException(
    message: String,
) : RuntimeException(message)
