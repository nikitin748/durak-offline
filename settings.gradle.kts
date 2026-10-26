pluginManagement {
    repositories {
        if (!providers.gradleProperty("engineOnly").isPresent) google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        if (!providers.gradleProperty("engineOnly").isPresent) google()
        mavenCentral()
    }
}

rootProject.name = "durak-offline"
include(":engine")
// The Android app needs Google's Maven repo. Pass -PengineOnly to build/test just the rules engine.
if (!providers.gradleProperty("engineOnly").isPresent) {
    include(":app")
}
