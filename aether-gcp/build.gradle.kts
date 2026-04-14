plugins {
    id("io.foundry.aether.java-conventions")
}

description = "GCP provider implementation for Aether"

dependencies {
    api(platform(aetherLibs.gcpBom))
    api(aetherLibs.gcpStorage)
}
