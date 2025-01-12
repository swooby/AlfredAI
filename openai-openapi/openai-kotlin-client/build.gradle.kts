plugins {
    kotlin("jvm")// version "2.0.20"
    //`maven-publish`
    id("com.diffplug.spotless") version "7.0.1"

}

group = "com.openai"
version = "0.0.1"

//repositories {
//    mavenCentral()
//}

//tasks.withType<KotlinCompile> {
//    kotlinOptions {
//        jvmTarget = "11"
//    }
//}
//kotlin {
//    jvmToolchain(11)
//}

// Use spotless plugin to automatically format code, remove unused import, etc
// To apply changes directly to the file, run `gradlew spotlessApply`
// Ref: https://github.com/diffplug/spotless/tree/main/plugin-gradle
//spotless {
//    // comment out below to run spotless as part of the `check` task
//    enforceCheck = false
//
//    format("misc") {
//        // define the files (e.g. '*.gradle', '*.md') to apply `misc` to
//        targe(".gitignore")
//
//        // define the steps to apply to those files
//        trimTrailingWhitespace()
//        indentWithSpaces() // Takes an integer argument if you don't like 4
//        endWithNewline()
//    }
//    kotlin {
//        ktfmt()
//    }
//}

//tasks.test {
//    useJUnitPlatform()
//}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    implementation(libs.okhttp3.okhttp)
    testImplementation(libs.kotlintest.runner.junit5)
}
