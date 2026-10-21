// SPDX-License-Identifier: LicenseRef-NexWrite-Proprietary
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "app.nexwrite.android"
    compileSdk = 36
    buildToolsVersion = "36.1.0"
    defaultConfig {
        applicationId = "app.nexwrite.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 20001
        versionName = "0.2.0-preview.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main") {
        kotlin.srcDirs(rootProject.file("../../core/bridge/kotlin"), rootProject.file("../../packages/protocol/generated"), rootProject.file("../../packages/protocol/api-generated"))
        jniLibs.srcDir(rootProject.file("../../target/android-jniLibs"))
        assets.srcDir(rootProject.file("../../target/android-assets"))
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { jniLibs.useLegacyPackaging = false }
    lint { abortOnError = true }
}
kotlin { jvmToolchain(17) }

dependencyLocking { lockAllConfigurations() }

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.foundation:foundation:1.10.1")
    implementation("androidx.compose.ui:ui:1.10.1")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.1")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.room:room-runtime:2.7.2")
    annotationProcessor("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.10.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.1")
}


// Input to the first-party notice generator. No dependency binaries enter Git.
tasks.register("runtimeComponentInventory") {
    doLast {
        val components = configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts.map { artifact ->
            mapOf("group" to artifact.moduleVersion.id.group, "name" to artifact.moduleVersion.id.name,
                "version" to artifact.moduleVersion.id.version, "file" to artifact.file.absolutePath)
        }.sortedBy { "${it["group"]}:${it["name"]}:${it["version"]}" }
        val output = rootProject.file("../../target/android-runtime-components.json")
        output.parentFile.mkdirs()
        output.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(components)))
    }
}

// A published APK must carry the notices from its actual resolved graph.
tasks.configureEach {
    if (name == "mergeReleaseAssets" || name == "mergeDebugAssets") {
        doFirst {
            val notices = rootProject.file("../../target/android-assets/notices/THIRD_PARTY_NOTICES.txt")
            val inventory = rootProject.file("../../target/android-assets/notices/inventory.json")
            check(notices.isFile && notices.length() > 0 && inventory.isFile && inventory.length() > 0) {
                "Generate Android notices from runtimeComponentInventory with tools/android-notices.mjs before packaging."
            }
        }
    }
}
