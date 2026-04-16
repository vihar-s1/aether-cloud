plugins {
    id("io.foundry.aether.java-conventions")
    `java-test-fixtures`
}

description = "Core abstractions for the Aether multi-cloud framework"

dependencies {
    testFixturesImplementation(aetherLibs.junitJupiter)
    testFixturesImplementation(aetherLibs.assertjCore)
}
