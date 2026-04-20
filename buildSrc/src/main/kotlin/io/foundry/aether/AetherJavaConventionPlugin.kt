package io.foundry.aether

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.repositories

class AetherJavaConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Apply Java library plugin
        project.pluginManager.apply("java-library")

        // Apply Spotless
        project.pluginManager.apply("com.diffplug.spotless")

        // Configure repository
        project.repositories {
            mavenCentral()
        }

        // Configure Java toolchain
        project.extensions.configure(JavaPluginExtension::class.java) {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        // Configure test suite
        project.tasks.withType(Test::class.java) {
            useJUnitPlatform()
        }

        // Create extension so modules can access versions
        project.extensions.create<AetherLibsExtension>("aetherLibs")

        // Common dependencies for all modules
        project.dependencies {
            add("api", AetherLibs.SLF4J_API)
            add("api", AetherLibs.GUAVA)
            add("api", AetherLibs.JACKSON_DATABIND)
            add("api", AetherLibs.COMMONS_LANG3)

            add("testImplementation", AetherLibs.JUNIT_JUPITER)
            add("testRuntimeOnly", AetherLibs.JUNIT_PLATFORM_LAUNCHER)
            add("testImplementation", AetherLibs.MOCKITO_CORE)
            add("testImplementation", AetherLibs.ASSERTJ_CORE)
        }

        // Configure Spotless — only check files changed since HEAD
        project.extensions.configure(SpotlessExtension::class.java) {
            ratchetFrom("HEAD")
            java {
                licenseHeader(
                    """/*
 * Copyright ${'$'}YEAR Foundry
 * SPDX-License-Identifier: Apache-2.0
 */

"""
                )
                eclipse().configFile("${project.rootDir}/configs/eclipse-formatter.xml")
                removeUnusedImports()
                trimTrailingWhitespace()
                endWithNewline()
            }
            kotlinGradle {
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
    }
}

open class AetherLibsExtension {
    // Core
    val slf4jApi = AetherLibs.SLF4J_API
    val guava = AetherLibs.GUAVA
    val jacksonDatabind = AetherLibs.JACKSON_DATABIND
    val commonsLang3 = AetherLibs.COMMONS_LANG3

    // AWS
    val awsBom = AetherLibs.AWS_BOM
    val awsSdkCore = AetherLibs.AWS_SDK_CORE

    // GCP
    val gcpBom = AetherLibs.GCP_BOM
    val gcpStorage = AetherLibs.GCP_STORAGE

    // Azure
    val azureCore = AetherLibs.AZURE_CORE

    // Extras
    val jacksonYaml = AetherLibs.JACKSON_YAML

    // Testing
    val junitJupiter = AetherLibs.JUNIT_JUPITER
    val junitPlatformLauncher = AetherLibs.JUNIT_PLATFORM_LAUNCHER
    val mockitoCore = AetherLibs.MOCKITO_CORE
    val assertjCore = AetherLibs.ASSERTJ_CORE
}
