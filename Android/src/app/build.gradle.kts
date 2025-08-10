plugins {
  alias(libs.plugins.android.application)
  // Note: set apply to true to enable google-services (requires google-services.json).
  alias(libs.plugins.google.services) apply true
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.hilt.application)
  kotlin("kapt")

}

android {
  namespace = "com.google.ai.edge.gallery"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.google.ai.edge.gallery"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "1.0.4"

    // Needed for HuggingFace auth workflows.
    manifestPlaceholders["appAuthRedirectScheme"] = "com.google.ai.edge.gallery.oauth"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
    freeCompilerArgs += "-Xcontext-receivers"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.material.icon.extended)
    
    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    androidTestImplementation("androidx.work:work-testing:2.9.0")
    
    // Data Storage
    implementation(libs.androidx.datastore)
    implementation(libs.com.google.code.gson)
    
    // CameraX (using BOM for version management)
    val cameraxVersion = "1.3.2"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")
    
    // Machine Learning
    implementation(libs.mediapipe.tasks.text)
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.mediapipe.tasks.imagegen)
    implementation(libs.tflite)
    implementation(libs.tflite.gpu)
    implementation(libs.tflite.support)
    
    // UI Components
    implementation(libs.commonmark)
    implementation(libs.richtext)
    implementation(libs.androidx.splashscreen)
    
    // Authentication
    implementation(libs.openid.appauth)
    
    // Google Services
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth.ktx)
    implementation("com.google.firebase:firebase-messaging-ktx")
    
    // Google Identity Services
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
    
    // Google Play Services (consolidated)
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    
    // Telegram API (TDLib)
    implementation("org.drinkless:tdlight:3.2.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    
    // Gmail API (consolidated)
    implementation("com.google.apis:google-api-services-gmail:v1-rev20220404-2.2.0") {
        exclude(group = "org.apache.httpcomponents")
        exclude(module = "guava-jdk5") // Avoid conflicts with Android's Guava version
    }
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    
    // Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    kapt(libs.hilt.android.compiler)
    
    // Protobuf
    implementation(libs.protobuf.javalite)
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

protobuf {
  protoc {
    artifact = "com.google.protobuf:protoc:3.21.7"
  }
  // Remove plugins section since we'll use the built-in Java plugin
  generateProtoTasks {
    all().forEach {
      it.builtins {
        create("java") {
          option("lite")
        }
      }
    }
  }
}
