buildscript {
    val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").exists()
    repositories {
        if (hasAndroidSdk) google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        if (hasAndroidSdk) classpath("com.android.tools.build:gradle:8.7.3")
    }
}

allprojects {
    repositories {
        val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
            System.getenv("ANDROID_SDK_ROOT") != null ||
            rootProject.file("local.properties").exists()
        if (hasAndroidSdk) google()
        mavenCentral()
    }
}
