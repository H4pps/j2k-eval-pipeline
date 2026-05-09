plugins {
    kotlin("jvm") version "2.3.20"
    application
}

group = "iurii.bulanov"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.snakeyaml:snakeyaml-engine:2.9")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("iurii.bulanov.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
