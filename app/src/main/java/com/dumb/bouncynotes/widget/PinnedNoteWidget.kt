package com.dumb.bouncynotes.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dumb.bouncynotes.MainActivity
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.buildPlainTextPreview
import com.dumb.bouncynotes.data.firstDisplayableImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Clave para el estado PROPIO de cada instancia del widget (una Preferences
// por GlanceId, manejada por Glance): qué nota le corresponde mostrar. Se
// escribe una sola vez, en PinnedNoteWidgetConfigActivity, al agregar el
// widget a la pantalla de inicio.
val KEY_PINNED_NOTE_ID = longPreferencesKey("pinnedNoteId")

class PinnedNoteWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val noteId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[KEY_PINNED_NOTE_ID]
        val note = noteId?.takeIf { it != 0L }?.let {
            NoteDatabase.getInstance(context).noteDao().getById(it)
        }
        // Miniatura de la primera imagen/gif de la nota, si tiene. Un widget
        // viaja por Binder con un límite chico de tamaño total, así que acá
        // se decodifica YA reducida (nunca la imagen original completa) para
        // no arriesgar un TransactionTooLargeException.
        val thumbnail = note?.let { firstDisplayableImage(it) }?.let { fileName ->
            withContext(Dispatchers.IO) { loadThumbnail(context, fileName) }
        }

        provideContent {
            GlanceTheme {
                PinnedNoteContent(context, note, thumbnail)
            }
        }
    }
}

private fun loadThumbnail(context: Context, fileName: String, maxDim: Int = 300): Bitmap? {
    val file = File(ImageStorage.imagesDir(context), fileName)
    if (!file.exists()) return null
    return try {
        // Dos pasadas: la primera solo mide (inJustDecodeBounds), para poder
        // calcular un inSampleSize y recién ahí decodificar de verdad — así
        // nunca se llega a cargar en memoria la imagen a resolución completa
        // (podría ser varios MB) para terminar mostrando 40dp de miniatura.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.absolutePath, opts)
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun PinnedNoteContent(context: Context, note: Note?, thumbnail: Bitmap?) {
    // Mismo extra ("openNoteId") que ya usa MainActivity para "abrí directo
    // en esta nota" (lo usan los recordatorios al tocar la notificación): el
    // widget reutiliza esa puerta de entrada en vez de inventar una nueva.
    // Es un Intent EXPLÍCITO (target = MainActivity), así que no choca con
    // la restricción de Android 14+ sobre PendingIntent mutables para
    // intents implícitos.
    val openIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("openNoteId", note?.id ?: 0L)
    }
    val openAction = actionStartActivity(openIntent)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
    ) {
        if (note == null) {
            // La nota asignada ya no existe (se borró para siempre) o el
            // widget todavía no terminó de configurarse. Todo este estado
            // es tocable (no hay cuerpo con scroll que le compita el gesto).
            Column(modifier = GlanceModifier.fillMaxSize().clickable(openAction)) {
                Text(
                    "Esta nota ya no está disponible.\nQuitá y volvé a agregar el widget para elegir otra.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        } else {
            // Encabezado (miniatura + título, hasta 2 líneas): es la ÚNICA
            // parte que abre la nota al tocar. El cuerpo de abajo tiene su
            // propio scroll, y si todo el widget fuera clickable a la vez,
            // el gesto de abrir competiría con el de scrollear.
            Row(
                modifier = GlanceModifier.fillMaxWidth().clickable(openAction),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                if (thumbnail != null) {
                    Image(
                        provider = ImageProvider(thumbnail),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.size(40.dp).cornerRadius(8.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }
                Text(
                    note.title.ifBlank { "(Sin título)" },
                    maxLines = 2,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            // Divisor: Glance no trae un composable "Divider" propio, así
            // que es una franja fina de 1dp con un color del tema.
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(GlanceTheme.colors.outline)
            ) {}
            Spacer(modifier = GlanceModifier.height(8.dp))
            // Cuerpo CON scroll: un Column normal no se puede scrollear en
            // Glance. Un LazyColumn con un único ítem sí — es el patrón que
            // recomienda la propia documentación de Glance para contenido
            // más largo que el alto visible del widget.
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                item {
                    Text(
                        buildPlainTextPreview(note),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            }
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
        // Preferences es INMUTABLE: no se puede hacer prefs[key] = valor
        // directo sobre lo que llega en el lambda, hay que pasar por
        // toMutablePreferences() y devolver ESE resultado.
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_PINNED_NOTE_ID] = noteId
            }
        }
        PinnedNoteWidget().update(context, glanceId)
        // Bug conocido de Glance (reportado por varios desarrolladores): en
        // el PRIMER placement de un widget con Activity de configuración,
        // este primer update() puede no "pegar" porque el widget todavía no
        // terminó de registrarse del todo del lado del sistema justo en ese
        // instante. Un segundo intento, un toque después, lo resuelve de
        // forma confiable — sin esto, quedaba mostrando "nota no disponible"
        // hasta que algo más disparara un refresh (por eso abrir la app y
        // volver a guardar la nota, o reconfigurar el widget, lo arreglaba).
        delay(600)
        PinnedNoteWidget().update(context, glanceId)
    }
}
