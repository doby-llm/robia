plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val robiaDebugKeystorePath = System.getenv("ROBIA_DEBUG_KEYSTORE_PATH")
val robiaDebugKeystorePassword = System.getenv("ROBIA_DEBUG_KEYSTORE_PASSWORD")
val robiaDebugKeyAlias = System.getenv("ROBIA_DEBUG_KEY_ALIAS")
val robiaDebugKeyPassword = System.getenv("ROBIA_DEBUG_KEY_PASSWORD")
val hasRobiaCiDebugSigning = listOf(
    robiaDebugKeystorePath,
    robiaDebugKeystorePassword,
    robiaDebugKeyAlias,
    robiaDebugKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.gusanitolabs.robia"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gusanitolabs.robia"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasRobiaCiDebugSigning) {
            create("robiaCiDebug") {
                storeFile = file(robiaDebugKeystorePath!!)
                storePassword = robiaDebugKeystorePassword!!
                keyAlias = robiaDebugKeyAlias!!
                keyPassword = robiaDebugKeyPassword!!
            }
        }
    }

    buildTypes {
        debug {
            if (hasRobiaCiDebugSigning) {
                signingConfig = signingConfigs.getByName("robiaCiDebug")
            }
        }

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
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":additional-info-core"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.play.services.auth)
    implementation(libs.tensorflow.lite)
    implementation(libs.skydoves.colorpicker.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
