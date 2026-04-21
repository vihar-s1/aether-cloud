rootProject.name = "aether"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

include("aether-core")
include("aether-inmemory")
include("aether-nfs")
include("aether-aws")
include("aether-gcp")