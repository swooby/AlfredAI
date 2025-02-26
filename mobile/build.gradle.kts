import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

fun ensureQuoted(input: String): String {
    return if (input.startsWith("\"") && input.endsWith("\"")) {
        input
    } else {
        "\"$input\""
    }
}

android {
    namespace = "com.swooby.alfredai"
    compileSdk = 35

    defaultConfig {
        minSdk = 34

        applicationId = "com.swooby.alfredai"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = gradleLocalProperties(rootDir, providers)
        val dangerousOpenApiKey = localProperties.getProperty("DANGEROUS_OPENAI_API_KEY") ?: ""
        buildConfigField("String", "DANGEROUS_OPENAI_API_KEY", ensureQuoted(dangerousOpenApiKey))
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview.android)
    implementation(libs.play.services.wearable)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))

    implementation(libs.jsoup)

    implementation(project(":openai"))
    implementation(project(":shared"))

    implementation(libs.twilio.audioswitch)
}