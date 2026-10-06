import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val playLicenseProperties = Properties().apply {
    val config = rootProject.file("play-licensing.properties")
    if (config.isFile) config.inputStream().use(::load)
}
val playLicensePublicKey = playLicenseProperties.getProperty("play.license.publicKey", "").filterNot(Char::isWhitespace)
val encodedPlayLicenseKey = playLicensePublicKey.takeIf { it.matches(Regex("[A-Za-z0-9+/=]*")) }.orEmpty()
val validateReleaseAcquisition = tasks.register("validateReleaseAcquisition") {
    doLast {
        check(playLicensePublicKey.isNotEmpty()) {
            "Ninho release bloqueado: configure play.license.publicKey em play-licensing.properties com a chave PUBLICA de licenciamento do app PAGO na Play Console. Debug continua disponível."
        }
        val key = runCatching { KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(playLicensePublicKey))) as RSAPublicKey }.getOrNull()
        check(key != null && key.modulus.bitLength() >= 2048) { "Chave PUBLICA Google Play inválida: use RSA X.509 Base64, 2048 bits ou superior. Não use chave privada, chave de upload ou certificado de assinatura." }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateReleaseAcquisition) }

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
        testInstrumentationRunner = "app.ninho.android.WidgetSmokeRunner"
        buildConfigField("String", "PLAY_LICENSE_PUBLIC_KEY", "\"$encodedPlayLicenseKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk { abiFilters += "arm64-v8a" }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
        add("secureTools", "org.apache.commons:commons-lang3:3.20.0")
        add("secureTools", "org.apache.httpcomponents:httpclient:4.5.14")
        add("secureTools", "org.bouncycastle:bcprov-jdk18on:1.85")
        add("secureTools", "org.bouncycastle:bcpkix-jdk18on:1.85")
        add("secureTools", "org.bouncycastle:bcutil-jdk18on:1.85")
    }

    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    debugImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")
    testImplementation("junit:junit:4.13.2")
    // Real JSON implementation on the host JVM for persisted-profile migration tests.
    testImplementation("org.json:json:20250517")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
