import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use(::load)
    }

fun setting(name: String): String? = providers.gradleProperty(name).orNull ?: localProperties.getProperty(name)

/** The production API. A release build always talks to it; a debug build can be pointed elsewhere (qeetid.apiUrl). */
val productionApi = "https://api.id.qeet.in"

/**
 * Firebase, for sign-in requests. Without app/google-services.json (it is not in the repository) the app builds, tests
 * and runs, and says on the linking screens that this build cannot receive requests. The file's values become the
 * string resources Firebase reads at start-up — what the google-services plugin would generate — so no extra plugin is
 * needed. See docs/firebase.md.
 */
val firebase: Map<String, String>? =
    file("google-services.json").takeIf { it.isFile }?.let { json ->
        @Suppress("UNCHECKED_CAST")
        val root = JsonSlurper().parse(json) as Map<String, Any?>
        val project = root["project_info"] as Map<String, Any?>
        val clients = root["client"] as List<Map<String, Any?>>
        val client =
            clients.firstOrNull {
                val info = it["client_info"] as Map<String, Any?>
                (info["android_client_info"] as Map<String, Any?>)["package_name"] == "com.qeetgroup.qeetid"
            } ?: error("app/google-services.json has no Android app for com.qeetgroup.qeetid")
        val info = client["client_info"] as Map<String, Any?>
        val apiKey = (client["api_key"] as List<Map<String, Any?>>).first()["current_key"] as String
        mapOf(
            "google_app_id" to info["mobilesdk_app_id"] as String,
            "gcm_defaultSenderId" to project["project_number"].toString(),
            "project_id" to project["project_id"] as String,
            "google_api_key" to apiKey,
            "google_storage_bucket" to (project["storage_bucket"] as String? ?: ""),
        )
    }

/** Release signing, from keystore.properties (never committed) when it exists; otherwise the release APK is unsigned. */
val keystoreProperties =
    rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use(::load) }
    }

android {
    namespace = "com.qeetgroup.qeetid"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.qeetgroup.qeetid"
        minSdk = 29
        targetSdk = 37
        versionCode = providers.gradleProperty("qeetid.versionCode").get().toInt()
        versionName = providers.gradleProperty("qeetid.versionName").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "FIREBASE_CONFIGURED", (firebase != null).toString())
        firebase?.forEach { (name, value) -> resValue("string", name, value) }
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // A debug build may talk to a development server on the LAN (cleartext is allowed only in debug builds,
            // src/debug/res/xml/network_security_config.xml): qeetid.apiUrl=http://10.0.2.2:4001 in local.properties.
            buildConfigField("String", "API_BASE_URL", "\"${setting("qeetid.apiUrl") ?: productionApi}\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$productionApi\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    testOptions {
        unitTests {
            // Robolectric renders the screens on the JVM with the app's and Qeetrix's resources (fonts, strings).
            isIncludeAndroidResources = true
            all {
                // Robolectric's Android 16 runtime needs jdk.internal.access on JDK 17+ (as in Qeetrix for Android).
                it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
            }
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = false
        // A new AGP or AndroidX release is not a defect in this repository; versions are reviewed deliberately.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // Libraries' own lint checks built for an older lint are skipped either way; the notice is not a finding.
        disable += "ObsoleteLintCustomCheck"
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ktlint {
    version.set(libs.versions.ktlint.engine)
    android.set(false)
}

dependencies {
    implementation(libs.qeetrix)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.core)
    implementation(libs.fragment)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.navigation.compose)
    implementation(libs.biometric)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    debugImplementation(libs.compose.ui.test.manifest)
}
