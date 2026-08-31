import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "io.github.chubbyhippo"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1.4")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null }
        }
    }
}

tasks.processResources {
    from(layout.projectDirectory.file(".ideameowrc"))
}

tasks.test {
    // Pre-allocate heap to avoid dynamic resizing and GC pause overhead during IDE bootstrap
    minHeapSize = "2g"
    maxHeapSize = "2g"
    jvmArgs(
        // High-throughput GC for batch test workloads
        "-XX:+UseParallelGC",
        // Pre-touch memory pages upfront to eliminate runtime page faults
        "-XX:+AlwaysPreTouch",
        // Run in headless mode to prevent GUI window initialization during tests
        "-Djava.awt.headless=true",
        // Disable unnecessary platform diagnostics and subsystems
        "-Didea.is.internal=false",
        "-Didea.auto.welcome=false",
        "-Dsun.awt.enableExtraMouseButtons=false",
    )
}

ktlint {
    version = "1.8.0"
}

detekt {
    buildUponDefaultConfig = true
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        extraWarnings = true
    }
}
