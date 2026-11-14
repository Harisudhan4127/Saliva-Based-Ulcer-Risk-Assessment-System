import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// ---------------------------------------------------------------------------
// Single source of truth: ../../shared
// ---------------------------------------------------------------------------
val sharedRoot = rootProject.file("../../shared")
val sharedVersion = groovy.json.JsonSlurper()
    .parse(sharedRoot.resolve("version.json")) as Map<*, *>
val appVersion = sharedVersion["version"] as String
val appVersionCode = (sharedVersion["versionCode"] as Number).toInt()
val modelVersion = sharedVersion["modelVersion"] as String
val swbraSpecVersion = sharedVersion["swbraSpecVersion"] as String
val preprocessingVersion = sharedVersion["preprocessingVersion"] as String
val updateRepo = providers.gradleProperty("suras.updateRepo").orElse("TODO_FILL").get()

fun quoted(value: Any?): String = "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""

// Copy the shared specs and ONNX model into generated assets so the APK can
// never drift from the desktop app. Nothing here is committed. AGP 9 requires
// generated directories to be registered through the variant Sources API.
abstract class SyncSharedAssetsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sharedDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        val source = sharedDir.get().asFile
        val target = outputDir.get().asFile
        target.deleteRecursively()
        SHARED_FILES.forEach { relative ->
            val from = source.resolve(relative)
            if (!from.isFile) throw GradleException("Missing shared asset: $relative")
            val to = target.resolve(relative)
            to.parentFile.mkdirs()
            from.copyTo(to, overwrite = true)
        }
    }

    private companion object {
        val SHARED_FILES = listOf(
            "version.json",
            "author.json",
            "preprocessing.json",
            "swbra_spec.json",
            "model/model.onnx",
            "model/metadata.json",
            "model/SHA256SUMS",
        )
    }
}

val syncSharedAssets = tasks.register<SyncSharedAssetsTask>("syncSharedAssets") {
    sharedDir.set(sharedRoot)
    outputDir.set(layout.buildDirectory.dir("generated/sharedAssets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            syncSharedAssets,
            SyncSharedAssetsTask::outputDir,
        )
    }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.harisudhan.suras"
    // androidx 1.12.x / lifecycle 2.11 / navigation 2.10 require compiling
    // against API 37. targetSdk stays one level behind (36) so we do not opt in
    // to new runtime behavior that has not been verified on a device.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.harisudhan.suras"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // The shared Rust core (scripts/build_android_core.sh) is only built for
        // these ABIs. Without this filter the APK would ship 32-bit ONNX Runtime
        // libraries but no matching core, so a 32-bit device would install and
        // then fail at runtime. Restricting to the ABIs we actually support also
        // roughly halves the APK size.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        buildConfigField("String", "MODEL_VERSION", quoted(modelVersion))
        buildConfigField("String", "SWBRA_SPEC_VERSION", quoted(swbraSpecVersion))
        buildConfigField("String", "PREPROCESSING_VERSION", quoted(preprocessingVersion))
        buildConfigField("String", "UPDATE_REPO", quoted(updateRepo))
    }

    signingConfigs {
        if (keystorePropertiesFile.isFile) {
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
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.isFile) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
        jniLibs {
            // SQLCipher, ONNX Runtime and JNA may each ship this shared runtime.
            pickFirsts += "**/libc++_shared.so"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        // Version-currency checks require network access and are handled by
        // Dependabot/OSV in CI instead.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.onnxruntime.android)
    implementation("net.zetetic:sqlcipher-android:${libs.versions.sqlcipher.get()}@aar")
    implementation(libs.androidx.sqlite)
    implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
}
