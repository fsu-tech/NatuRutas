plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
    id("kotlin-parcelize") // Añadido el plugin Parcelize
}

android {
    namespace = "com.example.gpxeditor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gpxeditor"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
// parcelize = true; Esta linea no es necesaria cuando usas el plugin id("kotlin-parcelize")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation(libs.play.services.location)
    implementation(libs.androidx.media3.common.ktx)
    kapt("com.github.bumptech.glide:compiler:4.15.1")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:1.8.20")
    implementation ("com.google.code.gson:gson:2.8.9")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation ("com.github.bumptech.glide:glide:4.12.0")

}
