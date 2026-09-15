import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.ninho.android"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "app.ninho.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

val secureTools = configurations.dependencyScope("secureTools")

configurations.configureEach {
    if (name.startsWith("unified-test-platform-") || name == "androidLintTool") {
        extendsFrom(secureTools.get())
    }
}

dependencies {
    add("secureTools", platform("io.netty:netty-bom:4.1.138.Final"))
    add("secureTools", platform("com.google.protobuf:protobuf-bom:3.25.5"))
    constraints {
        add("secureTools", "org.apache.commons:commons-lang3:3.18.0")
        add("secureTools", "org.apache.httpcomponents:httpclient:4.5.14")
        add("secureTools", "org.bouncycastle:bcprov-jdk18on:1.86")
        add("secureTools", "org.bouncycastle:bcpkix-jdk18on:1.86")
        add("secureTools", "org.bouncycastle:bcutil-jdk18on:1.86")
    }

    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    debugImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
