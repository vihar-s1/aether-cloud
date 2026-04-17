plugins {
    id("io.foundry.aether.java-conventions")
}

description = "NFS provider implementation for Aether"

dependencies {
    api(project(":aether-core"))
    testImplementation(testFixtures(project(":aether-core")))
}
