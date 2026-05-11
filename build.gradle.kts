plugins {
    kotlin("jvm") version "2.3.20"
    id("dev.detekt") version "2.0.0-alpha.3"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    jacoco
    application
}

group = "iurii.bulanov"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("org.snakeyaml:snakeyaml-engine:2.9")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    filter {
        exclude("**/build/**")
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
    basePath.set(projectDir)
}

jacoco {
    toolVersion = "0.8.14"
}

val jacocoCoverageExclusions =
    listOf(
        "**/MainKt.class",
        "**/*\$DefaultImpls.class",
        "**/benchmark/checkout/**",
        "**/process/**",
    )

application {
    mainClass.set("iurii.bulanov.MainKt")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(
            sourceSets.main.get().output.classesDirs.map { classDirectory ->
                fileTree(classDirectory) {
                    exclude(jacocoCoverageExclusions)
                }
            },
        ),
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(
            sourceSets.main.get().output.classesDirs.map { classDirectory ->
                fileTree(classDirectory) {
                    exclude(jacocoCoverageExclusions)
                }
            },
        ),
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.detekt)
    dependsOn(tasks.jacocoTestCoverageVerification)
}
