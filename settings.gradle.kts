pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Liquid Glass 液态玻璃库（QWEA0/Liquid-Glass-Android）
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "DeviceiNfo"
include(":app")
