package com.dumb.bouncynotes.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.update
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.GlanceTheme
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dumb.bouncynotes.MainActivity
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.buildPlainTextPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Clave para el estado PROPIO de cada instancia del widget (una Preferences
// por GlanceId, manejada por Glance): qué nota le corresponde mostrar. Se
// escribe una sola vez, en PinnedNoteWidgetConfigActivity, al agregar el
// widget a la pantalla de inicio.
val KEY_PINNED_NOTE_ID = longPreferencesKey("pinnedNoteId")

// Misma clave que ya usa MainActivity para "abrí directo en esta nota" (ver
// el comentario ahí: la usan los recordatorios al tocar la notificación). El
// widget reutiliza EXACTAMENTE esa puerta de entrada en vez de inventar una
// nueva: como ActionParameters.Key<Long>("openNoteId") viaja como extra del
// Intent con ese mismo nombre, MainActivity la recibe sin ningún cambio.
private val openNoteIdParam = ActionParameters.Key<Long>("openNoteId")

class PinnedNoteWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val noteId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[KEY_PINNED_NOTE_ID]
        val note = noteId?.takeIf { it != 0L }?.let {
            NoteDatabase.getInstance(context).noteDao().getById(it)
        }

        provideContent {
            GlanceTheme {
                PinnedNoteContent(note)
            }
        }
    }
}

@Composable
private fun PinnedNoteContent(note: Note?) {
    val openAction = actionStartActivity<MainActivity>(
        parameters = actionParametersOf(openNoteIdParam to (note?.id ?: 0L))
    )
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(openAction)
    ) {
        if (note == null) {
            // La nota asignada ya no existe (se borró para siempre) o el
            // widget todavía no terminó de configurarse. Igual lo dejamos
            // tocable: abre la app y desde ahí se puede volver a fijar otra.
            Text(
                "Esta nota ya no está disponible.\nQuitá y volvé a agregar el widget para elegir otra.",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
            )
        } else {
            Text(
                note.title.ifBlank { "(Sin título)" },
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                buildPlainTextPreview(note),
                maxLines = 6,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
            )
        }
    }
}

class PinnedNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PinnedNoteWidget()
}

// Punto de entrada único para refrescar el widget desde fuera (lo llama
// NoteRepository después de cualquier save/delete/purgeOldTrash). No hace
// falta que quien llama sea suspend: se dispara en una corrutina propia de
// IO y no se espera a que termine (igual que ya se hace, por ejemplo, para
// programar alarmas de recordatorio de forma "fire and forget").
object PinnedNoteWidgetUpdater {
    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            PinnedNoteWidget().updateAll(appContext)
        }
    }

    // La llama PinnedNoteWidgetConfigActivity al confirmar qué nota le
    // corresponde a ESTA instancia del widget en particular.
    suspend fun assignNote(context: Context, glanceId: GlanceId, noteId: Long) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs[KEY_PINNED_NOTE_ID] = noteId
        }
        PinnedNoteWidget().update(context, glanceId)
    }
}
