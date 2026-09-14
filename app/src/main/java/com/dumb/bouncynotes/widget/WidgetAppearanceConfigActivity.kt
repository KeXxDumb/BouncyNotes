package com.dumb.bouncynotes.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dumb.bouncynotes.data.ThemeMode
import com.dumb.bouncynotes.ui.SettingsViewModel
import com.dumb.bouncynotes.ui.theme.NotesTheme

// Config activity COMPARTIDA por los CUATRO widgets — acá solo hay
// apariencia (tema/fondo) para configurar, nunca elegir nota (eso es aparte
// para "Nota fijada", con su propio botón ChangeNote/Empty dentro del
// widget — ver PinnedNoteWidgetProvider — separado a propósito de esta
// pantalla). Se abre tanto al agregar cualquiera de los cuatro widgets
// (android:configure) como al reconfigurarlo después (long press >
// Configurar) — sin ningún ícono propio dentro del widget para esto,
// redundante con esa opción nativa.
class WidgetAppearanceConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                // Se lee el valor YA guardado (si se está reconfigurando un
                // widget existente, no uno recién agregado) para que la
                // pantalla arranque mostrando lo que ya estaba elegido, no
                // los valores por defecto siempre. Si nunca se configuró
                // nada, el valor "por defecto" que se lee acá ya sigue el
                // tema de la app (ver WidgetAppearancePrefs), no un
                // "Sistema" fijo aparte.
                var themeMode by remember { mutableStateOf(WidgetAppearancePrefs.getThemeMode(this, appWidgetId)) }
                var backgroundMode by remember { mutableStateOf(WidgetAppearancePrefs.getBackgroundMode(this, appWidgetId)) }
                ConfigScreen(
                    themeMode = themeMode,
                    backgroundMode = backgroundMode,
                    onThemeChange = { themeMode = it },
                    onBackgroundChange = { backgroundMode = it },
                    onSave = { saveAndFinish(themeMode, backgroundMode) }
                )
            }
        }
    }

    private fun saveAndFinish(themeMode: ThemeMode, backgroundMode: WidgetBackgroundMode) {
        WidgetAppearancePrefs.setAppearance(this, appWidgetId, themeMode, backgroundMode)

        // Esta Activity es compartida por los CUATRO providers — hay que
        // fijarse cuál es DUEÑO de este appWidgetId puntual (AppWidgetManager
        // lo sabe) para refrescar el correcto. Refrescar el equivocado
        // pintaría temporalmente el layout de un widget distinto encima de
        // este id hasta el próximo refresco real.
        val manager = AppWidgetManager.getInstance(this)
        when (manager.getAppWidgetInfo(appWidgetId)?.provider?.className) {
            PinnedNoteWidgetProvider::class.java.name ->
                PinnedNoteWidgetProvider.updateWidget(this, manager, appWidgetId)
            LastEditedNoteWidgetProvider::class.java.name ->
                LastEditedNoteWidgetProvider.updateWidget(this, manager, appWidgetId)
            AllNotesClockWidgetProvider::class.java.name ->
                AllNotesClockWidgetProvider.updateWidget(this, manager, appWidgetId)
            QuickActionsWidgetProvider::class.java.name ->
                QuickActionsWidgetProvider.updateWidget(this, manager, appWidgetId)
        }

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    themeMode: ThemeMode,
    backgroundMode: WidgetBackgroundMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBackgroundChange: (WidgetBackgroundMode) -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Apariencia del widget") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WidgetAppearancePicker(
                themeMode = themeMode,
                backgroundMode = backgroundMode,
                onThemeChange = onThemeChange,
                onBackgroundChange = onBackgroundChange
            )
            Button(onClick = onSave, modifier = Modifier.padding(16.dp)) {
                Text("Guardar")
            }
        }
    }
}
