plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Disable detekt for this module to avoid formatting auto-generated code
tasks.matching { it.name.contains("detekt", ignoreCase = true) }.configureEach {
    enabled = false
}

android {
    namespace = "uniffi.vss_rust_client_ffi"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    compileOnly(libs.jna)
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
