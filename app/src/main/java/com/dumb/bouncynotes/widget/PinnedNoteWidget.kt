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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dumb.bouncynotes.MainActivity
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.allInlineImageFileNames
import com.dumb.bouncynotes.data.buildPlainTextPreview
import com.dumb.bouncynotes.data.parseNoteContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Clave para el estado PROPIO de cada instancia del widget (una Preferences
// por GlanceId, manejada por Glance): qué nota le corresponde mostrar. Se
// escribe una sola vez, en PinnedNoteWidgetConfigActivity, al agregar el
// widget a la pantalla de inicio.
val KEY_PINNED_NOTE_ID = longPreferencesKey("pinnedNoteId")

// Un widget viaja por Binder con un límite chico de tamaño total: mostrar
// TODAS las imágenes de una nota con muchas fotos arriesgaría pasarse de
// ese límite. Se muestran como mucho estas, y el resto queda como aviso de
// texto ("+N más, abrí la nota").
private const val MAX_INLINE_IMAGES = 6

class PinnedNoteWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val noteId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[KEY_PINNED_NOTE_ID]
        val note = noteId?.takeIf { it != 0L }?.let {
            NoteDatabase.getInstance(context).noteDao().getById(it)
        }

        // Se arma la lista real de bloques (texto/imagen/galería/video) de
        // la nota, en el mismo orden en que aparecen — no un resumen
        // aplanado — para reproducir su posición real dentro del widget.
        // Los checklists no lo necesitan: no tienen imágenes.
        val parts = note?.takeIf { it.type == NoteType.TEXT }?.let { parseNoteContent(it.content) }

        val allImageNames = parts?.let { allInlineImageFileNames(it) }.orEmpty()
        val thumbnails: Map<String, Bitmap> = withContext(Dispatchers.IO) {
            allImageNames.take(MAX_INLINE_IMAGES).mapNotNull { fileName ->
                loadThumbnail(context, fileName)?.let { fileName to it }
            }.toMap()
        }

        provideContent {
            GlanceTheme {
                PinnedNoteContent(context, note, parts, thumbnails, allImageNames.size)
            }
        }
    }
}

private fun loadThumbnail(context: Context, fileName: String, maxDim: Int = 260): Bitmap? {
    val file = File(ImageStorage.imagesDir(context), fileName)
    if (!file.exists()) return null
    return try {
        // Dos pasadas: la primera solo mide (inJustDecodeBounds), para poder
        // calcular un inSampleSize y recién ahí decodificar de verdad — así
        // nunca se llega a cargar en memoria la imagen a resolución completa
        // para terminar mostrándola reducida.
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
private fun PinnedNoteContent(
    context: Context,
    note: Note?,
    parts: List<ContentPart>?,
    thumbnails: Map<String, Bitmap>,
    totalImageCount: Int
) {
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
            // widget todavía no terminó de configurarse.
            Column(modifier = GlanceModifier.fillMaxSize().clickable(openAction)) {
                Text(
                    "Esta nota ya no está disponible.\nQuitá y volvé a agregar el widget para elegir otra.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        } else {
            // Título (hasta 2 líneas): es la ÚNICA parte que abre la nota al
            // tocar. El cuerpo de abajo tiene su propio scroll, y si todo el
            // widget fuera clickable a la vez, el gesto de abrir competiría
            // con el de scrollear.
            Text(
                note.title.ifBlank { "(Sin título)" },
                maxLines = 2,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier.fillMaxWidth().clickable(openAction)
            )
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
            // Glance. Un LazyColumn sí — cada bloque de la nota (texto o
            // imagen) es un ítem separado, en el mismo orden real en que
            // aparecen en la nota.
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                if (note.type == NoteType.CHECKLIST) {
                    item {
                        Text(buildPlainTextPreview(note), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                    }
                } else {
                    parts?.forEach { part ->
                        when (part) {
                            is ContentPart.TextPart -> {
                                if (part.text.isNotBlank()) {
                                    item {
                                        Text(part.text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                                    }
                                }
                            }
                            is ContentPart.ImagePart -> {
                                thumbnails[part.fileName]?.let { bmp ->
                                    item {
                                        Image(
                                            provider = ImageProvider(bmp),
                                            contentDescription = part.caption,
                                            contentScale = ContentScale.Fit,
                                            modifier = GlanceModifier.fillMaxWidth().height(140.dp).cornerRadius(8.dp)
                                        )
                                    }
                                }
                            }
                            is ContentPart.GalleryPart -> {
                                // Simplificación: se muestran todas las
                                // imágenes del grupo apiladas una debajo de
                                // la otra, en vez de reproducir el layout
                                // real (grilla/carrusel) — no entra bien en
                                // el ancho de un widget.
                                part.fileNames.forEach { fileName ->
                                    thumbnails[fileName]?.let { bmp ->
                                        item {
                                            Image(
                                                provider = ImageProvider(bmp),
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = GlanceModifier.fillMaxWidth().height(140.dp).cornerRadius(8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            is ContentPart.VideoPart -> {
                                // Un widget no puede reproducir video ni
                                // decodificar un frame para miniatura acá.
                                item {
                                    Text(
                                        "🎬 " + part.caption.ifBlank { "Video" } + " — abrí la nota para verlo",
                                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                                    )
                                }
                            }
                        }
                    }
                    if (totalImageCount > MAX_INLINE_IMAGES) {
                        item {
                            Text(
                                "+ ${totalImageCount - MAX_INLINE_IMAGES} imagen(es) más — abrí la nota para verlas todas",
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                            )
                        }
                    }
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
    // corresponde a ESTA instancia del widget en particular. Usa el
    // overload SIMPLE de updateAppWidgetState (context, glanceId, lambda),
    // no el genérico con PreferencesGlanceStateDefinition explícito +
    // transform que devuelve un Preferences nuevo: ese overload genérico
    // era el que fallaba en el primer placement del widget.
    suspend fun assignNote(context: Context, glanceId: GlanceId, noteId: Long) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[KEY_PINNED_NOTE_ID] = noteId
        }
        PinnedNoteWidget().update(context, glanceId)
    }
}
