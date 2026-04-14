package io.foundry.aether

object AetherVersions {
    // Core dependencies
    const val SLF4J = "2.0.9"
    const val GUAVA = "33.0.0-jre"
    const val JACKSON = "2.16.1"
    const val COMMONS_LANG3 = "3.14.0"

    // Cloud SDKs
    const val AWS_SDK = "2.24.0"
    const val GCP_LIBRARIES_BOM = "26.32.0"
    const val GCP_STORAGE = "2.30.0"
    const val AZURE_CORE = "1.45.0"

    // Testing
    const val JUNIT = "5.10.1"
    const val JUNIT_PLATFORM_LAUNCHER = "1.10.1"
    const val MOCKITO = "5.8.0"
    const val ASSERTJ = "3.25.1"
}

object AetherLibs {
    // Core
    const val SLF4J_API = "org.slf4j:slf4j-api:${AetherVersions.SLF4J}"
    const val GUAVA = "com.google.guava:guava:${AetherVersions.GUAVA}"
    const val JACKSON_DATABIND = "com.fasterxml.jackson.core:jackson-databind:${AetherVersions.JACKSON}"
    const val COMMONS_LANG3 = "org.apache.commons:commons-lang3:${AetherVersions.COMMONS_LANG3}"

    // AWS
    const val AWS_BOM = "software.amazon.awssdk:bom:${AetherVersions.AWS_SDK}"
    const val AWS_SDK_CORE = "software.amazon.awssdk:sdk-core"

    // GCP
    const val GCP_BOM = "com.google.cloud:libraries-bom:${AetherVersions.GCP_LIBRARIES_BOM}"
    const val GCP_STORAGE = "com.google.cloud:google-cloud-storage:${AetherVersions.GCP_STORAGE}"

    // Azure
    const val AZURE_CORE = "com.azure:azure-core:${AetherVersions.AZURE_CORE}"

    // Testing
    const val JUNIT_JUPITER = "org.junit.jupiter:junit-jupiter:${AetherVersions.JUNIT}"
    const val JUNIT_PLATFORM_LAUNCHER = "org.junit.platform:junit-platform-launcher:${AetherVersions.JUNIT_PLATFORM_LAUNCHER}"
    const val MOCKITO_CORE = "org.mockito:mockito-core:${AetherVersions.MOCKITO}"
    const val ASSERTJ_CORE = "org.assertj:assertj-core:${AetherVersions.ASSERTJ}"
}
