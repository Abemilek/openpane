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

// Rendering fallbacks (see README, "Rendering problems").
val useTextureView: Boolean = providers.gradleProperty("renderBackend").getOrElse("surface") == "texture"
val softwareRender: Boolean = providers.gradleProperty("softwareRender").getOrElse("false").toBoolean()

// true  = native libraries are compressed inside the APK: a much smaller APK file,
//         but the installed app uses more storage (libraries are extracted at install).
// false = libraries are stored uncompressed and loaded straight from the APK:
//         bigger APK file, smallest installed size. This is the default.
val compressNativeLibs: Boolean = providers.gradleProperty("compressNativeLibs").getOrElse("false").toBoolean()

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
        versionCode = 3
        versionName = "0.2.1"

        buildConfigField("boolean", "USE_TEXTURE_VIEW", useTextureView.toString())
        buildConfigField("boolean", "SOFTWARE_RENDER", softwareRender.toString())

        ndk {
            abiFilters.addAll(abis)
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = compressNativeLibs
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
