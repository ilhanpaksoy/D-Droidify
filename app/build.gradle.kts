import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose)
}

// Signing: local keystore.properties (storeFile/storePassword/keyAlias/keyPassword)
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

// Version derived from git tag / env. Fork scheme: vX.Y.Z-N -> versionName "X.Y.Z-N", versionCode semver-based.
// Formula: versionCode = major*10000 + minor*100 + patch*10 + suffix  (suffix 0 if no dash, <10)
// Preserves existing 0.7.6 series: 0*10000+7*100+6*10+0=760, 0.7.6-5=765, 0.7.7=770, 1.0.0=10000, 1.0.0-2=10002.
// Monotonic and no hard-coded base; works for future 1.x without bumping.
// Tries: ENV (CI) -> exact tag on HEAD -> hardcoded fallback (0.7.6-5). Fallback only for local non-tag builds.
val derivedVersion: Pair<String, Int> = run {
    fun parseVersion(raw: String): Pair<String, Int> {
        val stripped = raw.removePrefix("v").trim().ifEmpty { "0.7.6-5" }
        // Split fork suffix: "X.Y.Z-N" -> base "X.Y.Z", suffix N
        val dashIdx = stripped.lastIndexOf('-')
        val (basePart, suffixStr) = if (dashIdx != -1) {
            val s = stripped.substring(dashIdx + 1)
            if (s.toIntOrNull() != null) stripped.substring(0, dashIdx) to s else stripped to ""
        } else stripped to ""
        val suffix = suffixStr.toIntOrNull() ?: 0
        // Require suffix <10 to avoid collision with patch*10 (documented)
        val suffixSafe = suffix.coerceIn(0, 9)
        if (suffix != suffixSafe) {
            // Log via println for visibility during config
            println("WARNING: fork suffix $suffix >=10, clamped to 9 to keep versionCode monotonic; bump patch instead")
        }
        val parts = basePart.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val code = major * 10000 + minor * 100 + patch * 10 + suffixSafe
        // Guard overflow (max 2100000000)
        require(code in 1..2100000000) { "versionCode $code out of range for $raw" }
        return stripped to code
    }
    val envTag = System.getenv("GITHUB_REF_NAME")
        ?: System.getenv("RELEASE_TAG")
    val gitTagExact = try {
        val result = providers.exec {
            commandLine("git", "describe", "--tags", "--exact-match", "HEAD")
            isIgnoreExitValue = true
        }
        val out = result.standardOutput.asText.get().trim()
        val code = result.result.get().exitValue
        if (code == 0 && out.isNotEmpty()) out else null
    } catch (_: Exception) { null }
    val raw = envTag ?: gitTagExact ?: "v0.7.6-5"
    parseVersion(raw)
}
val latestVersionName: String = derivedVersion.first
val latestVersionCode: Int = derivedVersion.second

android {
    namespace = "com.looker.droidify"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.ilhan.droidify"
        minSdk = 26
        versionName = latestVersionName
        versionCode = latestVersionCode

        testInstrumentationRunner = "com.looker.droidify.TestRunner"
    }

    androidResources.generateLocaleConfig = true

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProps.getProperty("storeFile")
            if (storeFilePath != null) {
                storeFile = if (File(storeFilePath).isAbsolute) file(storeFilePath) else rootProject.file(storeFilePath)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // storeType auto-detected (file is PKCS12 despite .jks extension)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard.pro",
            )
            // Use release signingConfig only if keystore.properties exists and storeFile valid
            val storeFilePath = keystoreProps.getProperty("storeFile")
            if (keystorePropsFile.exists() && storeFilePath != null) {
                val resolvedStoreFile = if (File(storeFilePath).isAbsolute) file(storeFilePath) else rootProject.file(storeFilePath)
                if (resolvedStoreFile.exists()) {
                    signingConfig = signingConfigs.getByName("release")
                }
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = ".d"
        }
        all {
            buildConfigField(
                type = "String",
                name = "VERSION_NAME",
                value = "\"v$latestVersionName\"",
            )
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "/DebugProbesKt.bin",
                "/kotlin/**.kotlin_builtins",
                "/kotlin/**.kotlin_metadata",
                "/META-INF/**.kotlin_module",
                "/META-INF/**.pro",
                "/META-INF/**.version",
                "/META-INF/{AL2.0,LGPL2.1,LICENSE*}",
                "/META-INF/versions/9/previous-**.bin",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.useJUnitPlatform()
                val processor = Runtime.getRuntime().availableProcessors() / 2
                if (processor > 1) it.maxParallelForks = processor
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters")
        optIn.add("kotlin.RequiresOptIn")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.core.ktx)
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.fragment.ktx)
    implementation(libs.lifecycle.viewModel)
    implementation(libs.recyclerview)
    implementation(libs.sqlite.ktx)

    implementation(libs.image.viewer)
    implementation(libs.bundles.coil)
    implementation(libs.quickie.foss)
    // Required for QuickieFOSS
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.datastore.core)
    implementation(libs.datastore.proto)

    implementation(libs.kotlin.stdlib)

    implementation(libs.bundles.coroutines)

    implementation(libs.libsu.core)
    implementation(libs.bundles.shizuku)
    implementation(libs.dhizuku.api)
    implementation(libs.hiddenapibypass)

    implementation(libs.jackson.core)
    implementation(libs.serialization)

    implementation(libs.okhttp)
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    implementation(libs.work.ktx)

    implementation(libs.hilt.core)
    implementation(libs.hilt.android)
    implementation(libs.hilt.ext.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.ext.compiler)

    // Compose dependencies
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    debugImplementation(libs.bundles.compose.debug)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.test.unit)
    testImplementation(libs.room.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.test.core)
    testImplementation(libs.test.core.ktx)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.hilt.test)
    testRuntimeOnly(libs.junit.platform)
    testRuntimeOnly(libs.junit.vintage.engine)
    kspTest(libs.hilt.compiler)
    androidTestImplementation(libs.hilt.test)
    androidTestImplementation(libs.room.test)
    androidTestImplementation(libs.bundles.test.android)
    kspAndroidTest(libs.hilt.compiler)

//    debugImplementation(libs.leakcanary)
}

// using a task as a preBuild dependency instead of a function that takes some time insures that it runs
// in /res are (almost) all languages that have a translated string is saved. this is safer and saves some time
task("detectAndroidLocals") {
    val langsList: MutableSet<String> = HashSet()

    // in /res are (almost) all languages that have a translated string is saved. this is safer and saves some time
    fileTree("src/main/res").visit {
        if (this.file.path.endsWith("strings.xml") &&
            this.file.canonicalFile.readText().contains("<string")
        ) {
            var languageCode = this.file.parentFile.name.replace("values-", "")
            languageCode = if (languageCode == "values") "en" else languageCode
            langsList.add(languageCode)
        }
    }
    val langsListString = "{${langsList.sorted().joinToString(",") { "\"${it}\"" }}}"
    android.defaultConfig.buildConfigField("String[]", "DETECTED_LOCALES", langsListString)
}
tasks.preBuild.dependsOn("detectAndroidLocals")
