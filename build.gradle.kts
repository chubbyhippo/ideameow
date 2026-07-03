import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.9.0"
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
        // Unified IntelliJ IDEA distribution (the IC/IU split ended with
        // 2025.3, so the intellijIdeaCommunity helper no longer applies).
        // Still a JBR-21 platform — jvmTarget stays 21 below.
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
            // IPGP 2.x would otherwise pin untilBuild to 261.* — clear it so
            // the plugin loads in future IDEs too.
            untilBuild = provider { null }
        }
    }
}

kotlin {
    // Build with a JDK 25 toolchain, but emit Java 21 bytecode: every current
    // IDE (through 2026.1) runs on JBR 21, so 21 is the highest classfile
    // version a plugin may ship. Raise jvmTarget only when the platform's
    // Java baseline moves.
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

// keep javac (no sources, but the task exists) on the same 21 target so
// KGP's jvm-target validation doesn't flag a 25/21 mismatch
tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}
