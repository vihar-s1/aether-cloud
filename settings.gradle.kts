rootProject.name = "aether"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

include("aether-core")
include("aether-aws")
include("aether-gcp")
include("aether-azure")
