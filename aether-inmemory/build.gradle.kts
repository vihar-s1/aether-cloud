plugins {
    id("io.foundry.aether.java-conventions")
}

description = "In-memory provider implementation for Aether"

dependencies {
    api(project(":aether-core"))
}
