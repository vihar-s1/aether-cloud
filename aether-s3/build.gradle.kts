plugins {
    id("io.foundry.aether.java-conventions")
}

description = "S3-compatible provider implementation for Aether (AWS, DigitalOcean, MinIO)"

dependencies {
    api(project(":aether-core"))
    testImplementation(testFixtures(project(":aether-core")))

    // AWS SDK
    api(platform("software.amazon.awssdk:bom:2.24.0"))
    api("software.amazon.awssdk:s3")
    api("software.amazon.awssdk:ec2")
    api("software.amazon.awssdk:kms")
    api("software.amazon.awssdk:sts")
    api("software.amazon.awssdk:secretsmanager")
}
