import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.9.0"
    // lint: ktlint (style/format) + detekt (smells, unused symbols) — both
    // run via `check`; `ktlintFormat` rewrites, detekt reports to
    // build/reports/detekt
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
        // Unified IntelliJ IDEA distribution (the IC/IU split ended with
        // 2025.3, so the intellijIdeaCommunity helper no longer applies).
        // Still a JBR-21 platform — hence the 21 toolchain/target below.
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

tasks.processResources {
    // the repo .ideameowrc is the plugin's whole default keymap (layout +
    // keypad table); Rc.defaults() parses it from the jar at runtime
    from(layout.projectDirectory.file(".ideameowrc"))
}

detekt {
    // defaults + detekt.yml overrides; detekt-baseline.xml freezes the
    // adoption-day findings so only NEW issues fail `check`
    buildUponDefaultConfig = true
    config.setFrom(files("detekt.yml"))
    baseline = file("detekt-baseline.xml")
}

kotlin {
    // JDK 21 toolchain: every current IDE (through 2026.1) runs on JBR 21,
    // so 21 is the highest classfile version a plugin may ship. Bump only
    // when the platform's Java baseline moves.
    jvmToolchain(21)
    compilerOptions {
        // pinned so a toolchain bump can't silently raise the bytecode version
        jvmTarget = JvmTarget.JVM_21
        // -Wextra: unused-parameter, var-never-written, unused-expression...
        // keep compiles warning-clean
        extraWarnings = true
    }
}
