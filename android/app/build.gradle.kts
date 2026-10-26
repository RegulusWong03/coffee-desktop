import java.io.File
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseBuildRequested = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
val releaseVersionCode = providers.gradleProperty("coffeeReleaseVersionCode").orNull?.toIntOrNull()
val releaseVersionName = providers.gradleProperty("coffeeReleaseVersionName").orNull
if (releaseBuildRequested && (releaseVersionCode == null || releaseVersionName.isNullOrBlank())) {
    throw GradleException("Release builds require -PcoffeeReleaseVersionCode and -PcoffeeReleaseVersionName.")
}

val signingPropertiesPath = providers.environmentVariable("COFFEE_SIGNING_PROPERTIES").orNull?.takeIf { it.isNotBlank() }
val releaseSigningProperties = signingPropertiesPath?.let { path ->
    val file = File(path)
    if (!file.isFile) throw GradleException("COFFEE_SIGNING_PROPERTIES does not point to a readable properties file.")
    Properties().apply {
        file.readLines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) return@forEach
            val separator = line.indexOf('=')
            if (separator <= 0) throw GradleException("Invalid signing property line.")
            setProperty(line.substring(0, separator).trim(), line.substring(separator + 1).trim())
        }
    }
}
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
if (releaseSigningProperties != null && signingKeys.any { releaseSigningProperties.getProperty(it).isNullOrBlank() }) {
    throw GradleException("The external release signing properties file is missing a required field.")
}
val releaseKeyStore = releaseSigningProperties?.let { properties ->
    val configured = File(properties.getProperty("storeFile"))
    if (configured.isAbsolute) configured else File(File(signingPropertiesPath!!).absoluteFile.parentFile, configured.path)
}
if (releaseBuildRequested && releaseSigningProperties == null) {
    throw GradleException("Release signing is required. Set COFFEE_SIGNING_PROPERTIES to the external signing.properties file.")
}
if (releaseBuildRequested && releaseKeyStore?.isFile != true) {
    throw GradleException("The release keystore configured outside the repository does not exist.")
}

android {
    namespace = "com.coffee.pet.android"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.coffee.pet.android"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode ?: 1
        versionName = releaseVersionName ?: "0.2-p1p2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningProperties != null) {
            create("release") {
                storeFile = releaseKeyStore
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningProperties != null) signingConfig = signingConfigs.getByName("release")
        }
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
