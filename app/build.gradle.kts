import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// La versión vive en version.properties y se cambia con scripts/bump-version.sh
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName = listOf("VERSION_MAJOR", "VERSION_MINOR", "VERSION_PATCH")
    .joinToString(".") { versionProps.getProperty(it) }
val appVersionCode = versionProps.getProperty("VERSION_CODE").toInt()

// Firma: ~/.config/medicomem/signing.properties (la crea scripts/setup-signing.sh).
// Sin ella, la release se firma con la clave de depuración (solo sirve para pruebas).
val signingProps = Properties().apply {
    val local = File(System.getProperty("user.home"), ".config/medicomem/signing.properties")
    if (local.exists()) local.inputStream().use { load(it) }
}
fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(name)

android {
    namespace = "com.medicomem"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.medicomem"
        minSdk = 24
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        setProperty("archivesBaseName", "medicomem-$appVersionName")
    }

    signingConfigs {
        signingValue("KEYSTORE_PATH")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = signingValue("KEYSTORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
