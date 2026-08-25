plugins {
    // AGP 8.13.0 (última de la serie 8.x compatible con Kotlin 2.3 y con
    // compileSdk 36): a propósito NO se salta a AGP 9.x, que exige migrar a
    // su "built-in Kotlin support" nuevo y a KSP en vez de kapt (Room usa
    // kapt acá) — un cambio grande y con más superficie de romperse en un
    // proyecto que se verifica a mano, sin compilador real disponible. AGP
    // 8.13 ya da compileSdk 36 (Android 16) y Kotlin 2.3, que es lo que
    // hace falta para que la app compile contra dispositivos nuevos.
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    // Desde Kotlin 2.0, el compilador de Compose se mudó al repo de Kotlin
    // y pasó a ser un plugin de Gradle aparte (ya no se configura con
    // composeOptions.kotlinCompilerExtensionVersion, que era la forma vieja
    // de Kotlin 1.9 que usaba este proyecto). La versión tiene que
    // coincidir exactamente con la de Kotlin de arriba.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}
