import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Native Android SDK 4.11.0 Community, extracted from the official distribution.
// No network download and no license in BuildConfig, source control or task output.
val easyarLocal = Properties().apply {
    rootProject.file("easyar.local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val easyarDir = rootProject.file(easyarLocal.getProperty("sdkDir", "vendor/easyar"))
val easyarSdkPresent = easyarDir.resolve("EasyAR.jar").isFile &&
    easyarDir.resolve("arm64-v8a/libEasyAR.so").isFile
val easyarLicense = easyarLocal.getProperty("licenseKey", "").trim()
val easyarAssets = layout.buildDirectory.dir("generated/easyarAssets")
val prepareEasyarLicense = tasks.register("prepareEasyarLicense") {
    // Always rewrite, including an empty key, so removing local config cannot leave a stale license.
    outputs.upToDateWhen { false }
    doLast {
        easyarAssets.get().file("easyar-license.txt").asFile.apply {
            parentFile.mkdirs()
            writeText(easyarLicense)
        }
    }
}
val verifyEasyarConfiguration = tasks.register("verifyEasyarConfiguration") {
    doLast {
        if (providers.gradleProperty("easyar.required").orNull == "true") {
            check(easyarSdkPresent) { "EasyAR 4.11.0 SDK missing. See EASYAR_CINEMA.md." }
            check(easyarLicense.isNotBlank()) { "EasyAR license missing from easyar.local.properties." }
        }
    }
}

// The version is pinned: every build names itself 2.0.1, and builds are told apart by the
// commit they were built from, shown next to the version — in the app ("2.0.1 (abc1234)")
// and in the release list. The commit is read at build time; without git the suffix is
// dropped and the plain 2.0.1 remains.
val commitSuffix = runCatching {
    val sha = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim()
    if (sha.isEmpty()) "" else " ($sha)"
}.getOrDefault("")

android {
    namespace = "com.samrat.cardboardhands"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.samrat.cardboardhands"
        minSdk = 29
        targetSdk = 35
        // Pinned: the release workflow never bumps these. The commit suffix in each flavor's
        // versionNameSuffix is what distinguishes one build from the next; the workflow reads
        // this literal line, so keep the "x.y.z" shape.
        versionCode = 15
        versionName = "2.0.1"
        // PhoneXR itself runs 64-bit; this keeps OpenCV and MediaPipe for other ABIs out of the APK.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        aidl = true
        // The editions give the app its own name through a generated string.
        resValues = true
    }

    packaging {
        jniLibs {
            // The C++ runtime the APK carries: :vins brings the one from the NDK it was compiled
            // with, OpenCV's AAR brings its own, and one APK has room for one file per path. The
            // module's copy wins because a project dependency merges before an external one, which
            // is the direction that matters — a newer libc++_shared.so still serves a library built
            // against an older one, and not the other way round.
            pickFirsts.add("lib/arm64-v8a/libc++_shared.so")
        }
    }

    /**
     * Two editions of PhoneXR. "Full" is everything. "Lite" is for a phone that cannot spare the
     * work: no face, no depth network, no voice assistant, no ARCore — hands, Joy-Con, games,
     * cinema and the home stay. The two install side by side, so a weaker phone can carry Lite
     * while the same account keeps the full one elsewhere.
     */
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            versionNameSuffix = commitSuffix
            buildConfigField("boolean", "LITE", "false")
            buildConfigField("boolean", "BE", "false")
            resValue("string", "app_label", "NextVR")
        }
        create("lite") {
            dimension = "edition"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "$commitSuffix-lite"
            buildConfigField("boolean", "LITE", "true")
            buildConfigField("boolean", "BE", "false")
            resValue("string", "app_label", "NextVR Lite")
        }
        // Cinema: 3DoF gaze controls, browser and media, with optional camera passthrough.
        create("be") {
            dimension = "edition"
            applicationIdSuffix = ".cinema"
            versionNameSuffix = "$commitSuffix-cinema"
            buildConfigField("boolean", "LITE", "true")
            buildConfigField("boolean", "BE", "true")
            resValue("string", "app_label", "NextVR Cinema")
        }
    }

    // Keep upstream signing for Full/Lite; Cinema uses the build machine's debug key.
    signingConfigs {
        create("upstreamDebug") {
            storeFile = file("src/main/assets/phonexr-signing.p12")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug { signingConfig = null }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    productFlavors.getByName("full").signingConfig = signingConfigs.getByName("upstreamDebug")
    productFlavors.getByName("lite").signingConfig = signingConfigs.getByName("upstreamDebug")
    productFlavors.getByName("be").signingConfig = signingConfigs.getByName("debug")

    // libvrapi.so of the Gear VR adapter, built by gearvr-shim (see gearvr-shim/STATUS.txt).
    // A local build wins; the checked-in copy lets CI (and a fresh clone) package the adapter.
    val shimBuild = rootProject.file("gearvr-shim/build/assets")
    sourceSets.getByName("main").assets.directories.add((if (shimBuild.isDirectory) shimBuild else rootProject.file("gearvr-shim/prebuilt")).path)
    sourceSets.getByName("be").apply {
        kotlin.directories.add(if (easyarSdkPresent) "src/easyar/java" else "src/noEasyar/java")
        assets.directories.add(easyarAssets.get().asFile.path)
        if (easyarSdkPresent) jniLibs.directories.add(easyarDir.path)
    }
    sourceSets.getByName("full").kotlin.directories.add("src/noEasyar/java")
    sourceSets.getByName("lite").kotlin.directories.add("src/noEasyar/java")

    // Models are memory-mapped straight from the APK (YOLO11, MediaPipe), so they stay uncompressed.
    androidResources {
        noCompress += listOf("tflite", "task")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    if (easyarSdkPresent) "beImplementation"(files(easyarDir.resolve("EasyAR.jar")))
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    val cameraX = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("androidx.camera:camera-mlkit-vision:$cameraX")

    // OrangeHanding: YOLO11 + MediaPipe (hands and body), brings tasks-vision and LiteRT along.
    implementation(project(":orangehanding"))
    // VINS-Mono's estimator, for the 6DoF mode that works without ARCore. Only the Full edition takes
    // it: its front end is OpenCV on every frame, which is what Lite leaves out by design, and BE has
    // no camera to track with.
    "fullImplementation"(project(":vins"))
    // Speech vs. other sounds for the face's mouth (YAMNet audio classifier).
    implementation("com.google.mediapipe:tasks-audio:0.10.35")
    // 6DoF in the VR home: ARCore tracks where the headset is in the room.
    implementation("com.google.ar:core:1.47.0")
    // Bundled QR recognizer: viewer profiles scan even when the optional Google Play
    // Code Scanner module is missing (common on custom ROMs and older phones).
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // Calls: Supabase Realtime over a WebSocket.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.android.tools.build:apksig:8.7.3")
    implementation("com.google.android.material:material:1.12.0")
    // The depth neural network (MiDaS) for 3D out of an ordinary photo; the model itself is downloaded on demand.
    // LiteRT (the new TensorFlow Lite) comes with :tracking; the same Interpreter API runs MiDaS.
    // ArUco markers on the Joy-Con for camera tracking; Lite goes without it (a 24 MB library).
    "fullImplementation"("org.opencv:opencv:4.14.0")
    // Shizuku runs the cinema display service as the shell user (virtual display + input for other apps).
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // An Apple HIG style interface: https://github.com/ienground/compose-hig
    implementation("zone.ien.hig:hig:1.4.1")
    // The second look of the interface — Material You (colors from the system wallpaper), switched in the settings.
    implementation("androidx.compose.material3:material3:1.5.0-alpha24")
    // Material You icons in the VR home (Horizon-style library and dock); R8 keeps only the used ones.
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    // hig only exposes them at runtime; the screens use them directly.
    implementation("androidx.compose.foundation:foundation:1.12.0")
    implementation("androidx.compose.ui:ui:1.12.0")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    // WebXR in the built-in browser: the polyfill must run before the page's own scripts.
    implementation("androidx.webkit:webkit:1.14.0")

    testImplementation("junit:junit:4.13.2")
}

tasks.matching { it.name.startsWith("mergeBe") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(prepareEasyarLicense)
}
tasks.matching { it.name == "preBeDebugBuild" || it.name == "preBeReleaseBuild" }.configureEach {
    dependsOn(verifyEasyarConfiguration)
}

