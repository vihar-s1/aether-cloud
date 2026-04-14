plugins {
    id("io.foundry.aether.java-conventions")
}

description = "Azure provider implementation for Aether"

dependencies {
    api(aetherLibs.azureCore)
}
