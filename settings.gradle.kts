pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    // provide plugin versions centrally so modules do not need versions
    plugins {
        id("com.android.application") version "8.13.1"
        id("com.android.library") version "8.13.1"
        id("org.jetbrains.kotlin.jvm") version "1.9.0"
        id("org.jetbrains.kotlin.android") version "1.9.0"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DRISHTI"
include(":app", ":collision", ":sensors", ":vision", ":network", ":fusion", ":ui", ":logging")