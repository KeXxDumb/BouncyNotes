plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Reemplaza a composeOptions.kotlinCompilerExtensionVersion (la forma
    // vieja, de Kotlin 1.9): desde Kotlin 2.0 el compilador de Compose vive
    // en el repo de Kotlin y se configura como plugin de Gradle aparte.
    id("org.jetbrains.kotlin.plugin.compose")
    id("kotlin-kapt")
}

android {
    namespace = "com.dumb.bouncynotes"
    // 36 = Android 16. Es el compileSdk más alto que todavía no exige saltar
    // a AGP 9.x (Compose 1.12/BOM 2026.08.00 en adelante ya piden compileSdk
    // 37 + AGP 9.1+; con AGP 8.13 el techo real es 36).
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dumb.bouncynotes"
        // Sin cambios a propósito: 23 es la base mínima que ya se venía
        // soportando, bajarle cobertura de dispositivos viejos no tiene que
        // ver con "que ande en dispositivos nuevos" (eso lo da compileSdk).
        minSdk = 23
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.${System.getenv("VERSION_CODE") ?: "0"}"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
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
    // OJO: composeOptions { kotlinCompilerExtensionVersion = ... } ya NO va
    // acá — se borró a propósito. Con el plugin org.jetbrains.kotlin.plugin.compose
    // aplicado arriba (versión 2.3.20, la misma que Kotlin), la versión del
    // compilador de Compose se resuelve sola; dejar las dos formas a la vez
    // pisa una a la otra de forma confusa.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// --- Notas de esta actualización de dependencias (agosto 2026) -----------
//
// Todas las versiones de acá fueron chequeadas contra la documentación
// oficial (developer.android.com, kotlinlang.org) al momento de escribir
// esto, no adivinadas de memoria — importante porque una versión inventada
// que no existe en el repositorio rompe el build de una (a diferencia de un
// bug de lógica, que al menos compila).
//
// Se decidió A PROPÓSITO NO hacer dos saltos más grandes, por riesgo
// desproporcionado en un proyecto que se verifica a mano (sin compilador):
//  1. AGP 9.x: exige migrar al nuevo "built-in Kotlin support" y básicamente
//     empuja a reemplazar kapt por KSP. Quedarse en AGP 8.13 (la última 8.x)
//     ya da compileSdk 36 + Kotlin 2.3, que es lo que hace falta para
//     dispositivos nuevos, sin la migración grande.
//  2. Coil 3.x: cambia el groupId (io.coil-kt -> io.coil-kt.coil3), el
//     paquete (coil -> coil3) y requiere declarar un artefacto de red
//     aparte. Es un cambio de API real en todos los AsyncImage del proyecto,
//     no solo un número de versión. Se actualiza Coil dentro de la serie 2.x
//     (2.6.0 -> 2.7.0, la última de esa serie) en vez de saltar a 3.x.
dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.12.3")
    implementation("androidx.fragment:fragment-ktx:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.10.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")

    // Sin cambios a propósito: 1.1.0 sigue siendo la última versión ESTABLE
    // de androidx.biometric (la 1.4.0 todavía está en alpha en la
    // documentación oficial al momento de esta actualización) — no hay una
    // versión estable más nueva a la que saltar todavía.
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Para que los GIF insertados en las notas se vean animados en vez de
    // quedarse en el primer cuadro como imagen estática.
    implementation("io.coil-kt:coil-gif:2.7.0")
    // Para poder reproducir los videos insertados en una nota (en el editor y
    // en el visor a pantalla completa).
    implementation("androidx.media3:media3-exoplayer:1.11.0")
    implementation("androidx.media3:media3-ui:1.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
}
