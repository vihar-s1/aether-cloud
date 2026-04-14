plugins {
    id("io.foundry.aether.java-conventions")
}

description = "AWS provider implementation for Aether"

dependencies {
    api(platform(aetherLibs.awsBom))
    api(aetherLibs.awsSdkCore)
}
