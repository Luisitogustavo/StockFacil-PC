plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "pe.edu.cibertec.stockfacil"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "pe.edu.cibertec.stockfacil"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
}
