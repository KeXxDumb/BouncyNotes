package com.example.notes

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.notes.data.NoteType
import com.example.notes.ui.BiometricHelper
import com.example.notes.ui.NoteEditScreen
import com.example.notes.ui.NoteListScreen
import com.example.notes.ui.NoteViewModel
import com.example.notes.ui.theme.NotesTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val viewModel: NoteViewModel = viewModel()
                    var biometricUnlocked by remember { mutableStateOf(false) }
                    val allLabels by viewModel.allLabels.collectAsState()

                    val requestBiometric: (onSuccess: () -> Unit) -> Unit = { onSuccess ->
                        if (BiometricHelper.canAuthenticate(this@MainActivity)) {
                            BiometricHelper.authenticate(
                                activity = this@MainActivity,
                                onSuccess = {
                                    biometricUnlocked = true
                                    onSuccess()
                                }
                            )
                        } else {
                            // Sin biometría/PIN configurado en el dispositivo: no se puede bloquear.
                            biometricUnlocked = true
                            onSuccess()
                        }
                    }

                    NavHost(navController = navController, startDestination = "list") {
                        composable("list") {
                            NoteListScreen(
                                viewModel = viewModel,
                                biometricUnlockedForPrivate = biometricUnlocked,
                                onRequestBiometric = { requestBiometric {} },
                                onNoteClick = { id -> navController.navigate("edit/$id?type=TEXT") },
                                onAddClick = { type -> navController.navigate("edit/0?type=${type.name}") }
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
                                viewModel = viewModel,
                                biometricUnlockedForPrivate = biometricUnlocked,
                                onRequestBiometric = { onSuccess -> requestBiometric(onSuccess) },
                                allLabels = allLabels,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
