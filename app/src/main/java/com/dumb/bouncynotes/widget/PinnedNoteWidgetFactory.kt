package com.dumb.bouncynotes.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.parseNoteContent
import kotlinx.coroutines.runBlocking
import java.io.File

// Un widget viaja por Binder con un límite chico de tamaño total: mostrar
// TODAS las imágenes de una nota con muchas fotos arriesgaría pasarse de
// ese límite. Se muestran como mucho estas, y el resto queda como aviso de
// texto ("+N más, abrí la nota").
private const val MAX_INLINE_IMAGES = 6

// Una fila de la lista del widget: o un bloque de texto (párrafo, ítem de
// checklist, o el aviso de "video"/"+N imágenes más"), o una imagen ya
// decodificada y reducida.
private sealed class Row {
    data class TextRow(val text: String) : Row()
    data class ImageRow(val bitmap: Bitmap) : Row()
}

class PinnedNoteWidgetFactory(
    private val context: Context,
    private val noteId: Long,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var note: Note? = null
    private var rows: List<Row> = emptyList()
    private var colors: WidgetColors = WidgetColors(R.drawable.widget_background_light, 0, 0)

    override fun onCreate() {}

    override fun onDestroy() {}

    // Las callbacks de un RemoteViewsFactory corren en un hilo de Binder
    // dedicado del sistema, ya fuera del hilo principal — por eso está bien
    // llamar acá, de forma bloqueante, tanto a Room (con runBlocking, ya
    // que NoteDao.getById es suspend) como a BitmapFactory.
    override fun onDataSetChanged() {
        val current = runBlocking { NoteDatabase.getInstance(context).noteDao().getById(noteId) }
        note = current
        rows = current?.let { buildRows(it) } ?: emptyList()
        colors = resolveWidgetColors(context)
    }

    private fun buildRows(note: Note): List<Row> {
        if (note.type == NoteType.CHECKLIST) {
            return note.checklistItems.map { item ->
                Row.TextRow((if (item.checked) "☑ " else "☐ ") + item.text)
            }
        }

        val parts = parseNoteContent(note.content)
        val result = mutableListOf<Row>()
        var imagesShown = 0
        var imagesTotal = 0

        fun addImage(fileName: String) {
            imagesTotal++
            if (imagesShown < MAX_INLINE_IMAGES) {
                loadThumbnail(context, fileName)?.let { bmp ->
                    result += Row.ImageRow(bmp)
                    imagesShown++
                }
            }
        }

        parts.forEach { part ->
            when (part) {
                is ContentPart.TextPart -> if (part.text.isNotBlank()) result += Row.TextRow(part.text)
                is ContentPart.ImagePart -> addImage(part.fileName)
                // Simplificación: se muestran todas las imágenes del grupo
                // apiladas una debajo de otra, en vez de reproducir el
                // layout real (grilla/carrusel) — no entra bien en el ancho
                // de un widget.
                is ContentPart.GalleryPart -> part.fileNames.forEach { addImage(it) }
                // Un widget no puede reproducir video ni decodificar un
                // frame para miniatura acá.
                is ContentPart.VideoPart -> result += Row.TextRow(
                    "🎬 " + part.caption.ifBlank { "Video" } + " — abrí la nota para verlo"
                )
            }
        }

        if (imagesTotal > MAX_INLINE_IMAGES) {
            result += Row.TextRow("+ ${imagesTotal - MAX_INLINE_IMAGES} imagen(es) más — abrí la nota para verlas todas")
        }

        return result
    }

    override fun getCount(): Int = if (note != null) 1 + rows.size else 0

    override fun getViewAt(position: Int): RemoteViews {
        if (position == 0) return getHeaderView()
        return when (val row = rows[position - 1]) {
            is Row.TextRow -> getTextRowView(row)
            is Row.ImageRow -> getImageRowView(row)
        }
    }

    private fun getHeaderView(): RemoteViews {
        val currentNote = note
        return RemoteViews(context.packageName, R.layout.pinned_note_widget_header).apply {
            setTextViewText(R.id.Title, currentNote?.title?.ifBlank { "(Sin título)" } ?: "")
            setTextColor(R.id.Title, colors.textPrimary)
            // El click de "abrir la nota" va en Title específicamente, NO en
            // HeaderRow (el contenedor que envuelve a Title y a ChangeNote):
            // dos vistas ANIDADAS (contenedor + hijo) con manejadores de
            // click distintos es un problema conocido en listas de widgets
            // — el sistema puede disparar los dos a la vez, o ninguno de
            // forma confiable. Al ser hermanas (mismo nivel, sin superponerse),
            // cada una responde solo a su propio toque.
            setOnClickFillInIntent(R.id.Title, PinnedNoteWidgetProvider.openNoteFillInIntent(noteId))
            setOnClickFillInIntent(R.id.ChangeNote, PinnedNoteWidgetProvider.reconfigureFillInIntent(widgetId))
        }
    }

    private fun getTextRowView(row: Row.TextRow): RemoteViews =
        RemoteViews(context.packageName, R.layout.pinned_note_widget_text_row).apply {
            setTextViewText(R.id.RowText, row.text)
            setTextColor(R.id.RowText, colors.textSecondary)
            setOnClickFillInIntent(R.id.RowText, PinnedNoteWidgetProvider.openNoteFillInIntent(noteId))
        }

    private fun getImageRowView(row: Row.ImageRow): RemoteViews =
        RemoteViews(context.packageName, R.layout.pinned_note_widget_image_row).apply {
            setImageViewBitmap(R.id.RowImage, row.bitmap)
            setOnClickFillInIntent(R.id.RowImage, PinnedNoteWidgetProvider.openNoteFillInIntent(noteId))
        }

    override fun getViewTypeCount() = 3

    override fun hasStableIds() = false

    override fun getLoadingView(): RemoteViews? = null

    override fun getItemId(position: Int): Long = position.toLong()
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
