package com.example.notes

import android.os.Bundle
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.notes.data.FontScale
import com.example.notes.data.NoteType
import com.example.notes.data.ThemeMode
import com.example.notes.ui.BiometricHelper
import com.example.notes.ui.NoteEditScreen
import com.example.notes.ui.NoteListScreen
import com.example.notes.ui.NoteViewModel
import com.example.notes.ui.SettingsScreen
import com.example.notes.ui.SettingsViewModel
import com.example.notes.ui.theme.NotesTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.settings.collectAsState()

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
                            NavHost(navController = navController, startDestination = "list") {
                                composable("list") {
                                    NoteListScreen(
                                        viewModel = noteViewModel,
                                        settings = settings,
                                        biometricUnlockedForPrivate = biometricValid,
                                        onRequestBiometric = { requestBiometric {} },
                                        onNoteClick = { id -> navController.navigate("edit/$id?type=TEXT") },
                                        onAddClick = { type -> navController.navigate("edit/0?type=${type.name}") },
                                        onOpenSettings = { navController.navigate("settings") }
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
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                composable("settings") {
                                    SettingsScreen(
                                        settings = settings,
                                        noteViewModel = noteViewModel,
                                        onUpdate = { transform -> settingsViewModel.update(transform) },
                                        onBack = { navController.popBackStack() }
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
