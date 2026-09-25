pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "PixelPlumberLand"

include(":core")

// Die Android-App wird nur gebaut, wenn ein Android SDK vorhanden ist.
// So lassen sich Spiellogik und Tests auch ohne SDK ausführen.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").exists()
if (hasAndroidSdk) include(":app")
