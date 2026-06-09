import com.android.build.api.dsl.ApplicationExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Read the embedded webhook bearer token from local.properties
// (key: agent.webhook.token). Falls back to "dev-token" so debug
// builds still compile on machines that haven't configured it.
val agentDebugToken: String = run {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(props::load)
    props.getProperty("agent.debug.token")
        ?: System.getenv("AGENT_DEBUG_TOKEN")
        ?: ""
}

val releaseKeystorePath: String? = System.getenv("KEYSTORE_FILE")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

fun gitValue(vararg args: String): String? {
    return try {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        if (process.waitFor() == 0) output.takeIf { it.isNotEmpty() } else null
    } catch (_: Exception) {
        null
    }
}

val baseVersionName = (findProperty("APP_VERSION_NAME") as String?)?.trim().orEmpty()
    .ifEmpty { "1.0" }
val gitCommitCount = gitValue("rev-list", "--count", "HEAD")
    ?.toIntOrNull()
    ?.coerceAtLeast(1)
    ?: 1
val gitCommitSha = gitValue("rev-parse", "--short=8", "HEAD") ?: "local"
val resolvedVersionName = "$baseVersionName.$gitCommitCount-$gitCommitSha"
val hasReleaseSigning = releaseKeystorePath != null && file(releaseKeystorePath).exists()

extensions.configure<ApplicationExtension> {
    namespace = "com.example.notification_agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.notification_agent"
        minSdk = 30
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = resolvedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AGENT_DEBUG_TOKEN", "\"$agentDebugToken\"")
        buildConfigField("String", "GIT_COMMIT_SHA", "\"$gitCommitSha\"")
    }

    signingConfigs {
        // Release signing driven by CI secrets. When the keystore env vars are
        // absent (e.g. branch artifacts / local builds), fall back to the
        // Android debug signing config so the APK is still installable.
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

extensions.configure<KotlinAndroidProjectExtension> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.material)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Compose-based bank-config UI lives in its own module so the Compose
    // compiler plugin does not run in the same module as Room/KSP.
    implementation(project(":configui"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

val packageReleaseArtifacts by tasks.registering {
    group = "build"
    description = "Copies the release APK to versioned APK/ZIP artifacts."
    dependsOn("assembleRelease")

    doLast {
        val releaseDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val sourceApk = releaseDir.resolve("app-release.apk")
        check(sourceApk.exists()) { "Release APK not found at ${sourceApk.absolutePath}" }

        val artifactBaseName = "notification-agent-v${resolvedVersionName}-release"
        val artifactDir = layout.buildDirectory.dir("outputs/dist/release").get().asFile.apply {
            mkdirs()
        }
        val versionedApk = artifactDir.resolve("$artifactBaseName.apk")
        val versionedZip = artifactDir.resolve("$artifactBaseName.zip")

        sourceApk.copyTo(versionedApk, overwrite = true)

        ant.invokeMethod(
            "zip",
            mapOf(
                "destfile" to versionedZip,
                "basedir" to artifactDir,
                "includes" to versionedApk.name,
                "update" to false
            )
        )

        println("Packaged ${versionedApk.name}")
        println("Packaged ${versionedZip.name}")
    }
}
