plugins {
    id("io.foundry.aether.java-conventions")
}

description = "AWS provider implementation for Aether (S3, EC2, Secrets Manager, KMS)"

dependencies {
    api(project(":aether-core"))
    testImplementation(testFixtures(project(":aether-core")))

    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:2.34.0"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:ec2")
    runtimeOnly("software.amazon.awssdk:kms")
    runtimeOnly("software.amazon.awssdk:sts")
    implementation("software.amazon.awssdk:secretsmanager")

    // Testcontainers (integration tests)
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.4"))
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-localstack")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}
