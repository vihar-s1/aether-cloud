plugins {
    id("io.foundry.aether.java-conventions")
}

description = "Azure provider implementation for Aether (Blob Storage, Key Vault, Compute)"

dependencies {
    api(project(":aether-core"))
    testImplementation(testFixtures(project(":aether-core")))

    // CVE-2026-33871: force patched Netty across all Azure SDK transitive deps
    constraints {
        implementation("io.netty:netty-codec-http2") { version { require(aetherLibs.nettyVersion) } }
        implementation("io.netty:netty-handler") { version { require(aetherLibs.nettyVersion) } }
        implementation("io.netty:netty-codec-http") { version { require(aetherLibs.nettyVersion) } }
    }

    // Azure SDK BOM + services
    implementation(platform("com.azure:azure-sdk-bom:1.3.6"))
    implementation("com.azure:azure-storage-blob")
    implementation("com.azure:azure-security-keyvault-secrets")
    implementation("com.azure:azure-identity")

    // Azure Resource Manager (Compute) — not in azure-sdk-bom
    implementation("com.azure.resourcemanager:azure-resourcemanager-compute:2.46.0")

    // Testcontainers (integration tests — Azurite for Blob Storage)
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.4"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}
