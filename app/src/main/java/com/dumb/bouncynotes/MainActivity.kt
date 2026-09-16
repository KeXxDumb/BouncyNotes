package com.dumb.bouncynotes

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dumb.bouncynotes.data.FontScale
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.ThemeMode
import com.dumb.bouncynotes.ui.BiometricHelper
import com.dumb.bouncynotes.ui.NoteEditScreen
import com.dumb.bouncynotes.ui.NoteListScreen
import com.dumb.bouncynotes.ui.NoteViewModel
import com.dumb.bouncynotes.ui.SettingsScreen
import com.dumb.bouncynotes.ui.SettingsViewModel
import com.dumb.bouncynotes.ui.theme.NotesTheme

class MainActivity : FragmentActivity() {

    // BUG (reportado): tocar "abrir nota" desde un widget no abría nada; y
    // cuando sí llegaba a abrirse (la primera vez, con la app cerrada), no
    // se podía volver a la lista con el botón atrás — había que cerrar la
    // app entera y reabrirla.
    //
    // Causas reales, dos bugs distintos apilados:
    //
    // 1) El Intent del widget solo tenía FLAG_ACTIVITY_NEW_TASK. La
    //    documentación de esa flag es explícita: "si ya hay una tarea
    //    corriendo para la actividad que se está por iniciar, no se inicia
    //    una actividad nueva; en cambio, la tarea actual simplemente se trae
    //    al frente tal cual estaba". O sea, con la app ya abierta en
    //    background, tocar el widget NO vuelve a entregar el Intent — solo
    //    resucita la pantalla que hubiera quedado abierta, ignorando el
    //    "abrir esta nota". Por eso "no pasaba nada".
    //
    //    El primer intento de arreglo fue sumar FLAG_ACTIVITY_CLEAR_TOP,
    //    pero el bug seguía: con launchMode "standard" (el que usa esta
    //    Activity, no hay otro declarado en el manifest), CLEAR_TOP solo
    //    fuerza la recreación si la actividad se encuentra DENTRO de la
    //    tarea que se está por traer al frente — pero el comportamiento de
    //    "traer la tarea al frente TAL CUAL estaba" de NEW_TASK (citado
    //    arriba) tiene prioridad y corta el proceso antes de que CLEAR_TOP
    //    llegue a aplicarse. El arreglo real es FLAG_ACTIVITY_CLEAR_TASK
    //    (no CLEAR_TOP): en vez de buscar una instancia para reemplazar
    //    DENTRO de la tarea existente, borra la tarea entera y arranca una
    //    Activity nueva sí o sí — así se confirmó comparando con NotallyX
    //    (la app de referencia de este proyecto), que usa exactamente esta
    //    combinación para lo mismo. Como esta app es de una sola Activity,
    //    "borrar la tarea entera" no pierde nada: el back stack real (la
    //    lista de notas, etc.) lo maneja el NavController de Compose
    //    ADENTRO de esta única Activity, no Android a nivel de tareas.
    //
    // 2) Aunque el Intent se procesara bien, `openNoteId` decidía el
    //    startDestination del NavHost — es decir, la nota se abría como la
    //    RAÍZ del back stack, sin ninguna pantalla de lista debajo. Atrás
    //    no tenía a dónde volver: cerraba la Activity directamente. Se
    //    resolvió arrancando SIEMPRE en "list", y navegando a la nota
    //    DESPUÉS (con un LaunchedEffect, una vez que el NavHost ya existe)
    //    — así la lista queda como raíz real del back stack y atrás
    //    funciona como cualquier otra navegación.
    //
    // `pendingOpenNoteId`/`pendingNewNoteType` viven como campos de la
    // Activity (no dentro del Composable) para que `onNewIntent` — que NO
    // puede tocar código dentro de `setContent {}` directamente — los pueda
    // actualizar también, por si en el futuro algún flujo SÍ llega a
    // entregar el Intent por ahí en vez de recrear la Activity.
    private var pendingOpenNoteId by mutableStateOf<Long?>(null)
    private var pendingNewNoteType by mutableStateOf<String?>(null)

    companion object {
        // Vive en un companion object (estático, en memoria) a propósito:
        // sobrevive a que la Activity se recree (girar la pantalla, cambios
        // de configuración — pasa SIEMPRE en esta app, no hay
        // android:configChanges en el manifest, ver bug #11 en
        // estado-actual.md) pero se resetea solo cuando el PROCESO entero
        // muere y se relanza, que es lo que de verdad significa "abrir la
        // app" para el sorteo de imagen de fondo. Si esto viviera en
        // rememberSaveable o en el ViewModel, girar la pantalla (que
        // recrea la Activity pero NO el proceso) volvería a sortear una
        // imagen distinta en cada rotación, no en cada apertura real.
        private var backgroundRotatedThisProcess = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val rawOpenNoteId = intent.getLongExtra("openNoteId", 0L)
        Log.d("BouncyNotesWidget", "MainActivity.onNewIntent() openNoteId=$rawOpenNoteId newNoteType=${intent.getStringExtra("newNoteType")}")
        rawOpenNoteId.takeIf { it != 0L }?.let { pendingOpenNoteId = it }
        intent.getStringExtra("newNoteType")?.let { pendingNewNoteType = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Si la actividad se abrió desde la notificación de un recordatorio,
        // vamos a esa nota (por encima de la lista, no en su lugar — ver
        // comentario de arriba).
        val rawOpenNoteId = intent?.getLongExtra("openNoteId", 0L) ?: 0L
        Log.d("BouncyNotesWidget", "MainActivity.onCreate() openNoteId=$rawOpenNoteId newNoteType=${intent?.getStringExtra("newNoteType")}")
        pendingOpenNoteId = rawOpenNoteId.takeIf { it != 0L }
        // Si se abrió desde el widget de "acciones rápidas" (nueva nota /
        // nuevo checklist), vamos a una nota en blanco de ese tipo — misma
        // convención que ya usa el botón "+" de la lista (noteId=0 significa
        // "todavía no existe, se crea al guardar").
        pendingNewNoteType = intent?.getStringExtra("newNoteType")
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            // El parpadeo de fondo negro / valores de fábrica al abrir la
            // app ya no se resuelve acá escondiendo la UI mientras se
            // espera un valor real: se resuelve en el origen, sembrando
            // SettingsViewModel.settings con una copia sincrónica de los
            // últimos valores reales guardados (ver SettingsCache y el
            // comentario en SettingsViewModel). Así, esta primerísima
            // composición ya tiene el tema/colores/fondo correctos, sin
            // tener que distinguir "todavía no sé" de "ya sé".
            val settings by settingsViewModel.settings.collectAsState()

            // Sorteo de fondo de imagen (ver comentario del companion
            // object de arriba sobre por qué el guard vive ahí y no acá).
            // Corre como mucho una vez por proceso; si la rotación no está
            // activa o el pool está vacío, no hace nada (queda el
            // backgroundImagePath que ya estuviera guardado de antes).
            LaunchedEffect(Unit) {
                if (!backgroundRotatedThisProcess) {
                    backgroundRotatedThisProcess = true
                    if (settings.backgroundImageRotationEnabled && settings.backgroundImagePaths.isNotEmpty()) {
                        val chosen = settings.backgroundImagePaths.random()
                        settingsViewModel.update { it.copy(backgroundImagePath = chosen) }
                    }
                }
            }

            LaunchedEffect(settings.hideFromRecents) {
                if (settings.hideFromRecents) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val fontMultiplier = when (settings.fontScale) {
                FontScale.SMALL -> 0.9f
                FontScale.MEDIUM -> 1.0f
                FontScale.LARGE -> 1.15f
            }

            NotesTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                seedColorHex = settings.seedColorHex
            ) {
                val view = LocalView.current
                val systemBarColor = MaterialTheme.colorScheme.background.toArgb()
                if (!view.isInEditMode) {
                    SideEffect {
                        window.statusBarColor = systemBarColor
                        window.navigationBarColor = systemBarColor
                        WindowCompat.getInsetsController(window, view).apply {
                            isAppearanceLightStatusBars = !darkTheme
                            isAppearanceLightNavigationBars = !darkTheme
                        }
                    }
                }
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontMultiplier)
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        val noteViewModel: NoteViewModel = viewModel()
                        var lastUnlockedAt by remember { mutableStateOf(0L) }
                        val allLabels by noteViewModel.allLabels.collectAsState()

                        val requestBiometric: (onSuccess: () -> Unit) -> Unit = { onSuccess ->
                            if (BiometricHelper.canAuthenticate(this@MainActivity)) {
                                BiometricHelper.authenticate(
                                    activity = this@MainActivity,
                                    onSuccess = {
                                        lastUnlockedAt = System.currentTimeMillis()
                                        onSuccess()
                                    }
                                )
                            } else {
                                lastUnlockedAt = System.currentTimeMillis()
                                onSuccess()
                            }
                        }

                        val rememberMinutes = settings.biometricRememberMinutes
                        val biometricValid = if (lastUnlockedAt == 0L) {
                            false
                        } else if (rememberMinutes < 0) {
                            true
                        } else if (rememberMinutes == 0) {
                            false
                        } else {
                            val elapsedMinutes = (System.currentTimeMillis() - lastUnlockedAt) / 60000
                            elapsedMinutes < rememberMinutes
                        }

                        if (settings.appWideBiometricLock && !biometricValid) {
                            AppLockScreen(onUnlock = { requestBiometric {} })
                        } else {
                            // Se navega DESPUÉS de que el NavHost ya exista
                            // (no como startDestination — ver comentario
                            // arriba de la clase) para que "list" quede como
                            // raíz real del back stack. Se usa
                            // navController.navigate() directo, no
                            // navigateSafe(): esta es una navegación
                            // programática única disparada por el Intent,
                            // no un toque del usuario, así que la protección
                            // "anti doble-toque fantasma" de navigateSafe
                            // (que exige que la pantalla actual ya esté
                            // RESUMED) no aplica y de hecho podría llegar a
                            // ignorar esta navegación si se ejecuta antes de
                            // que "list" termine de asentarse.
                            LaunchedEffect(pendingOpenNoteId) {
                                pendingOpenNoteId?.let { id ->
                                    Log.d("BouncyNotesWidget", "LaunchedEffect navegando a edit/$id?type=TEXT")
                                    navController.navigate("edit/$id?type=TEXT")
                                    pendingOpenNoteId = null
                                }
                            }
                            LaunchedEffect(pendingNewNoteType) {
                                pendingNewNoteType?.let { type ->
                                    navController.navigate("edit/0?type=$type")
                                    pendingNewNoteType = null
                                }
                            }
                            NavHost(
                                navController = navController,
                                startDestination = "list"
                            ) {
                                composable("list") {
                                    NoteListScreen(
                                        viewModel = noteViewModel,
                                        settings = settings,
                                        biometricUnlockedForPrivate = biometricValid,
                                        onRequestBiometric = { requestBiometric {} },
                                        onNoteClick = { id -> navController.navigateSafe("edit/$id?type=TEXT") },
                                        onAddClick = { type -> navController.navigateSafe("edit/0?type=${type.name}") },
                                        onOpenSettings = { navController.navigateSafe("settings") }
                                    )
                                }
                                composable(
                                    route = "edit/{noteId}?type={type}",
                                    arguments = listOf(
                                        navArgument("noteId") { type = NavType.LongType },
                                        navArgument("type") {
                                            type = NavType.StringType
                                            defaultValue = "TEXT"
                                        }
                                    )
                                ) { backStackEntry ->
                                    val noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L
                                    val typeArg = backStackEntry.arguments?.getString("type") ?: "TEXT"
                                    val initialType = try {
                                        NoteType.valueOf(typeArg)
                                    } catch (e: Exception) {
                                        NoteType.TEXT
                                    }
                                    NoteEditScreen(
                                        noteId = noteId,
                                        initialType = initialType,
                                        viewModel = noteViewModel,
                                        settings = settings,
                                        biometricUnlockedForPrivate = biometricValid,
                                        onRequestBiometric = { onSuccess -> requestBiometric(onSuccess) },
                                        allLabels = allLabels,
                                        onBack = { navController.popBackStackSafe() }
                                    )
                                }
                                composable("settings") {
                                    SettingsScreen(
                                        settings = settings,
                                        noteViewModel = noteViewModel,
                                        onUpdate = { transform -> settingsViewModel.update(transform) },
                                        onBack = { navController.popBackStackSafe() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLockScreen(onUnlock: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("Notes está bloqueada")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onUnlock) { Text("Desbloquear") }
        }
    }
    LaunchedEffect(Unit) { onUnlock() }
}

// --- Navegación "a prueba de doble toque" ------------------------------
//
// Bug reportado: abrir Ajustes, cerrarlo, y tocar rápido y repetidamente la
// esquina superior izquierda (donde vive el botón de menú) podía dejar la
// pantalla completamente negra, sin ningún elemento visible.
//
// La causa raíz es un problema clásico de Navigation Compose: cuando un
// composable dispara navigate()/popBackStack() más de una vez antes de que
// la transición anterior termine de procesarse (por ejemplo, por dos toques
// casi simultáneos, uno de ellos "fantasma" mientras la pantalla anterior
// todavía se estaba recomponiendo tras volver de Ajustes), el NavController
// puede terminar procesando una navegación sobre un back stack que ya está
// en medio de otro cambio, dejando el "currentBackStackEntry" en un estado
// inconsistente: la pantalla vieja ya se descompuso pero la nueva nunca
// llegó a componerse, resultando en una pantalla en blanco/negra sin ningún
// error visible en Logcat.
//
// La solución recomendada por el propio equipo de Android (ver el patrón
// "navigateSingleTopTo" usado en apps de referencia como Now In Android) es
// verificar que el back stack entry actual ya esté en estado RESUMED antes
// de permitir una nueva navegación: si todavía no llegó a RESUMED es porque
// la transición previa sigue en curso, y ese segundo toque (el "fantasma")
// se ignora en vez de dispararse.
private fun NavController.navigateSafe(route: String) {
    val resumed = currentBackStackEntry
        ?.lifecycle
        ?.currentState
        ?.isAtLeast(Lifecycle.State.RESUMED) ?: true
    if (resumed) {
        navigate(route)
    }
}

private fun NavController.popBackStackSafe() {
    val resumed = currentBackStackEntry
        ?.lifecycle
        ?.currentState
        ?.isAtLeast(Lifecycle.State.RESUMED) ?: true
    if (resumed) {
        popBackStack()
    }
}
