import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp) version(libs.versions.ksp)
    alias(libs.plugins.gradle.ktlint) version(libs.versions.ktlint)
    alias(libs.plugins.jetbrainsComposeCompiler)
}

android {
    namespace = "com.koalasat.pokey"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.koalasat.pokey"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "v0.0.5-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    if (System.getenv("SIGN_RELEASE") != null) {
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        val keystoreProperties = Properties().apply {
            load(FileInputStream(keystorePropertiesFile))
        }

        signingConfigs {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            if (System.getenv("SIGN_RELEASE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isMinifyEnabled = true
            resValue("string", "app_name", "@string/app_name_release")
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "app_name", "@string/app_name_debug")
        }
    }

    splits {
        abi {
            reset()
            include("x86", "x86_64", "arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.okhttp)
    implementation(libs.security.crypto.ktx)
    implementation(libs.quartz) {
        exclude("net.java.dev.jna")
    }
    implementation(libs.ammolite) {
        exclude("net.java.dev.jna")
    }
    implementation(libs.jna) { artifact { type = "aar" } }
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.androidx.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
