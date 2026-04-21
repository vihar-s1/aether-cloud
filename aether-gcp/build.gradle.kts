plugins {
    id("io.foundry.aether.java-conventions")
}

description = "GCP provider implementation for Aether"

dependencies {
    api(project(":aether-core"))
    testImplementation(testFixtures(project(":aether-core")))

    implementation(platform("com.google.cloud:libraries-bom:26.80.0"))
    implementation("com.google.cloud:google-cloud-storage")
    implementation("com.google.cloud:google-cloud-secretmanager")
    implementation("com.google.cloud:google-cloud-compute")
    runtimeOnly("com.google.cloud:google-cloud-kms")

    // Testcontainers (integration tests)
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.4"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}
