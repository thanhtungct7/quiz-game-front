plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase reads its project config from google-services.json, downloaded from the Firebase
// console into this directory. Without the file the app still builds and runs, with push
// notifications switched off -- see PushRepository.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// --- libGDX native libraries -------------------------------------------------
//
// The `gdx-platform` natives artifacts are plain jars holding a bare `libgdx.so`, with the ABI
// carried only by the classifier, so AGP cannot package them as they come. They are pulled
// through a configuration of their own and laid out as `<abi>/libgdx.so`, which is then
// registered as a jniLibs source directory below.
val gdxAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

val gdxNatives: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

// Resolved to a plain File: AGP 9 refuses a Provider in the SourceSet API, because it
// cannot tell a generated directory from a hand-edited one. The path is static anyway;
// `preBuild` below is what guarantees the directory is filled before anything reads it.
val gdxNativesDir: Directory = layout.buildDirectory.dir("gdxJniLibs").get()

val copyGdxNatives by tasks.registering(Sync::class) {
    into(gdxNativesDir)
    gdxAbis.forEach { abi ->
        from(provider {
            gdxNatives.files
                .filter { it.name.endsWith("natives-$abi.jar") }
                .map { zipTree(it) }
        }) {
            include("*.so")
            into(abi)
        }
    }
}

android {
    namespace = "com.kma.quiz_game"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.kma.quiz_game"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Host machine's LAN IP, for testing from a physical device on the same Wi-Fi.
        // Must also be whitelisted in res/xml/network_security_config.xml (cleartext HTTP).
        buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.8:8000/api/v1/\"")

        // Must match the backend's GOOGLE_WEB_CLIENT_ID (see duo-game-back/.env) -- GoogleSignIn
        // issues a Google ID token whose audience is this *Web* OAuth client, not an Android
        // one, even though it's requested from the Android app.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"664465470290-b50a1e1cbkt0hm297mggnk3kno09hlvi.apps.googleusercontent.com\"",
        )
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("main").jniLibs.srcDir(gdxNativesDir.asFile)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.play.services.auth)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.gdx.core)
    implementation(libs.gdx.backend.android)
    gdxAbis.forEach { abi ->
        gdxNatives(variantOf(libs.gdx.platform) { classifier("natives-$abi") })
    }
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.named("preBuild") { dependsOn(copyGdxNatives) }
