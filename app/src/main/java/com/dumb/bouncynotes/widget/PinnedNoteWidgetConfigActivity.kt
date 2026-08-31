package com.dumb.bouncynotes.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.NoteRepository
import com.dumb.bouncynotes.data.ThemeMode
import com.dumb.bouncynotes.data.buildPlainTextPreview
import com.dumb.bouncynotes.ui.SettingsViewModel
import com.dumb.bouncynotes.ui.theme.NotesTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PinnedNoteWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Boilerplate estándar de una config activity de widget: si el
        // usuario se echa atrás sin elegir ninguna nota, el sistema tiene
        // que enterarse de que la colocación del widget se canceló (si no,
        // queda un widget "roto" en la pantalla de inicio sin nota asignada).
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            NotesTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                seedColorHex = settings.seedColorHex
            ) {
                ConfigScreen(onNoteChosen = ::assignAndFinish)
            }
        }
    }

    private fun assignAndFinish(noteId: Long) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@PinnedNoteWidgetConfigActivity)
                .getGlanceIdBy(appWidgetId)
            PinnedNoteWidgetUpdater.assignNote(this@PinnedNoteWidgetConfigActivity, glanceId, noteId)

            val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

@Composable
private fun ConfigScreen(onNoteChosen: (Long) -> Unit) {
    val context = LocalContext.current
    // Repositorio propio y liviano, sin pasar `context` (no hace falta:
    // esta pantalla solo LEE notas, nunca llama a save()/delete(), así que
    // no hay ningún refresh de widget que disparar desde acá).
    val repository = remember { NoteRepository(NoteDatabase.getInstance(context).noteDao()) }
    // Se filtran notas borradas (papelera) y privadas: no tendría sentido
    // ofrecerlas para un widget que queda visible en la pantalla de inicio,
    // sin ningún bloqueo biométrico de por medio.
    val notesFlow = remember {
        repository.notes.map { list -> list.filter { it.deletedAt == null && !it.isPrivate } }
    }
    val notes by notesFlow.collectAsState(initial = null)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Elegí una nota para el widget") }) }
    ) { padding ->
        when {
            notes == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            notes!!.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Todavía no tenés notas para fijar acá.\nCreá una nota primero y volvé a agregar el widget.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(notes!!, key = { it.id }) { note ->
                    ListItem(
                        headlineContent = {
                            Text(note.title.ifBlank { "(Sin título)" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(buildPlainTextPreview(note), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        modifier = Modifier.clickable { onNoteChosen(note.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
