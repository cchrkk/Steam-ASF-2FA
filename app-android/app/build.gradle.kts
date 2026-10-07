plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
	id("org.jetbrains.kotlin.plugin.compose")
	id("org.jetbrains.kotlin.plugin.serialization")
}

// Version can be overridden by CI for releases: -PversionName=2.0.1 -PversionCode=100000042
val versionNameOverride = (project.findProperty("versionName") as String?)?.takeIf { it.isNotBlank() }
val versionCodeOverride = (project.findProperty("versionCode") as String?)?.toIntOrNull()

// Release signing, from environment (CI secrets). Without it the app is built debug-signed.
val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val keystoreKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val keystoreKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
val hasReleaseSigning = !keystorePath.isNullOrBlank() && File(keystorePath).exists() && !keystorePassword.isNullOrBlank() && !keystoreKeyAlias.isNullOrBlank()

android {
	namespace = "tk.chrk.qrloginapprover"
	compileSdk = 34

	defaultConfig {
		applicationId = "tk.chrk.qrloginapprover"
		minSdk = 26
		targetSdk = 34
		versionCode = versionCodeOverride ?: 1
		versionName = versionNameOverride ?: "1.0"
	}

	signingConfigs {
		if (hasReleaseSigning) {
			create("release") {
				storeFile = File(keystorePath!!)
				storePassword = keystorePassword
				keyAlias = keystoreKeyAlias
				keyPassword = keystoreKeyPassword
			}
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			if (hasReleaseSigning) {
				signingConfig = signingConfigs.getByName("release")
			}
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = "17"
	}

	buildFeatures {
		compose = true
	}

	packaging {
		resources {
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}
	}
}

dependencies {
	val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
	implementation(composeBom)

	implementation("androidx.core:core-ktx:1.13.1")
	implementation("androidx.activity:activity-compose:1.9.2")
	implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
	implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.ui:ui-tooling-preview")
	implementation("androidx.compose.material3:material3")
	implementation("androidx.compose.material:material-icons-extended")

	implementation("androidx.camera:camera-core:1.4.0")
	implementation("androidx.camera:camera-camera2:1.4.0")
	implementation("androidx.camera:camera-lifecycle:1.4.0")
	implementation("androidx.camera:camera-view:1.4.0")

	implementation("com.google.mlkit:barcode-scanning:17.3.0")

	implementation("androidx.security:security-crypto:1.1.0-alpha06")

	implementation("com.squareup.okhttp3:okhttp:4.12.0")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

	implementation("io.coil-kt:coil-compose:2.7.0")

	debugImplementation("androidx.compose.ui:ui-tooling")
}
