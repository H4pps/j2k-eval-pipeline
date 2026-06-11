import org.gradle.process.CommandLineArgumentProvider

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.16.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "iurii.bulanov"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":"))

    intellijPlatform {
        intellijIdeaCommunity("2025.2.6.1") {
            useInstaller = false
        }
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        id = "iurii.bulanov.j2k-runner"
        name = "J2K Runner Plugin"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "252"
            untilBuild = "252.*"
        }
    }
}

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    filter {
        exclude("**/build/**")
    }
}

tasks.named<JavaExec>("runIde") {
    workingDir = rootProject.projectDir

    val benchmarkConfig = providers.gradleProperty("benchmarkConfig")
    val kindProp = providers.gradleProperty("kind")
    val stagingDir = providers.gradleProperty("stagingDir")
    val generatedKotlinDir = providers.gradleProperty("generatedKotlinDir")
    val conversionReport = providers.gradleProperty("conversionReport")
    val logsDir = providers.gradleProperty("logsDir")
    val indexingTimeoutMs = providers.gradleProperty("indexingTimeoutMs")
    val ideaRuntimeDir = providers.gradleProperty("ideaRuntimeDir")

    // The Kotlin plugin mode is JVM-global and chosen at IDE startup: K2 converter needs it on,
    // K1 converters (old/new) need it off.
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            val useK2 = kindProp.orNull == "k2"
            buildList {
                add("-Djava.awt.headless=true")
                add("-Didea.kotlin.plugin.use.k2=$useK2")
                add("-DbytecodeAnalysis.index.enabled=false")
                indexingTimeoutMs.orNull?.let { add("-Dj2k.indexing.timeoutMs=$it") }
                ideaRuntimeDir.orNull?.let {
                    val runtimeDir = rootProject.file(it)
                    add("-Didea.config.path=${runtimeDir.resolve("config").path}")
                    add("-Didea.system.path=${runtimeDir.resolve("system").path}")
                    add("-Didea.log.path=${runtimeDir.resolve("log").path}")
                }
            }
        },
    )

    doFirst {
        val configPath =
            benchmarkConfig.orNull
                ?: throw GradleException("Missing required property: -PbenchmarkConfig=benchmarks/hikaricp.yml")
        val runnerArgs =
            mutableListOf(
                "j2k-convert",
                "--config",
                rootProject.file(configPath).path,
                "--kind",
                kindProp.orNull ?: "k1-old-dumb",
            )

        stagingDir.orNull?.let { runnerArgs += listOf("--staging-dir", it) }
        generatedKotlinDir.orNull?.let { runnerArgs += listOf("--generated-kotlin-dir", it) }
        conversionReport.orNull?.let { runnerArgs += listOf("--conversion-report", it) }
        logsDir.orNull?.let { runnerArgs += listOf("--logs-dir", it) }

        setArgs(runnerArgs)
    }
}
