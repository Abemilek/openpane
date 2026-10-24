import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val geckoviewVersion: String = providers.gradleProperty("geckoviewVersion").get()
val abis: List<String> = providers.gradleProperty("abis")
    .getOrElse("arm64-v8a")
    .split(",")
    .map { it.trim() }
    .filter { it.isNotEmpty() }

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.openpane.client"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.openpane.client"
        minSdk = 29
        // Keep target 34 to avoid mandatory API 35+ edge-to-edge behavior.
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"

        ndk {
            abiFilters.addAll(abis)
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview:$geckoviewVersion")
    implementation("androidx.activity:activity:1.10.1")
    // UniFFI's generated Kotlin bindings use JNA to load libopenpane_core.so.
    implementation("net.java.dev.jna:jna:5.19.1@aar")
}
