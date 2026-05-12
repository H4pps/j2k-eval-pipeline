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
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Didea.kotlin.plugin.use.k2=false",
                "-DbytecodeAnalysis.index.enabled=false",
            )
        },
    )

    val benchmarkConfig = providers.gradleProperty("benchmarkConfig")
    val stagingDir = providers.gradleProperty("stagingDir")
    val generatedKotlinDir = providers.gradleProperty("generatedKotlinDir")
    val conversionReport = providers.gradleProperty("conversionReport")
    val logsDir = providers.gradleProperty("logsDir")

    doFirst {
        val configPath =
            benchmarkConfig.orNull
                ?: throw GradleException("Missing required property: -PbenchmarkConfig=benchmarks/hikaricp.yml")
        val runnerArgs =
            mutableListOf(
                "j2k-convert",
                "--config",
                rootProject.file(configPath).path,
            )

        stagingDir.orNull?.let { runnerArgs += listOf("--staging-dir", it) }
        generatedKotlinDir.orNull?.let { runnerArgs += listOf("--generated-kotlin-dir", it) }
        conversionReport.orNull?.let { runnerArgs += listOf("--conversion-report", it) }
        logsDir.orNull?.let { runnerArgs += listOf("--logs-dir", it) }

        setArgs(runnerArgs)
    }
}
