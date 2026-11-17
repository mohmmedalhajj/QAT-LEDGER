import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

val qatLicensePublicKeys: String =
    ((project.findProperty("QAT_LICENSE_PUBLIC_KEYS") as String?)
        ?: System.getenv("QAT_LICENSE_PUBLIC_KEYS")
        ?: "").trim()

fun escapeBuildConfig(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
val hasReleaseKeystore = keystorePropertiesFile.exists()
if (hasReleaseKeystore) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.aistudio.tofanqat.nxzwmb"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.aistudio.tofanqat.nxzwmb"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        buildConfigField("String", "QAT_LICENSE_PUBLIC_KEYS", "\"${escapeBuildConfig(qatLicensePublicKeys)}\"")
    }

    flavorDimensions += "edition"
    productFlavors {
        create("customer") {
            dimension = "edition"
            buildConfigField("String", "APP_EDITION", "\"customer\"")
        }
        create("developer") {
            dimension = "edition"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "APP_EDITION", "\"developer\"")
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                val sf = keystoreProperties.getProperty("storeFile")
                if (!sf.isNullOrBlank()) {
                    val appRel = file(sf)
                    storeFile = if (appRel.exists()) appRel else rootProject.file(sf)
                }
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                println("[QAT-LEDGER] android/key.properties not found; signing release APK with Android debug key (for clean install/QA only — provide original keystore via android/key.properties for store/upgrade continuity).")
                signingConfig = signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

flutter {
    source = "../.."
}
