plugins {
    id("io.foundry.aether.java-conventions")
}

description = "AWS provider implementation for Aether (S3, EC2, Secrets Manager, KMS)"

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
