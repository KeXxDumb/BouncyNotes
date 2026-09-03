package com.dumb.bouncynotes.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.parseNoteContent
import java.io.File

// Un widget viaja por Binder con un límite chico de tamaño total: mostrar
// TODAS las imágenes de una nota con muchas fotos arriesgaría pasarse de
// ese límite. Se muestran como mucho estas, y el resto queda como aviso de
// texto ("+N más, abrí la nota").
private const val MAX_INLINE_IMAGES = 6

// Una fila de la lista de un widget de "una nota completa": bloque de
// texto (párrafo, ítem de checklist, o el aviso de "video"/"+N imágenes
// más"), o una imagen ya decodificada y reducida. Compartida entre el
// widget de "nota fijada" y el de "última nota editada" — la única
// diferencia entre esos dos widgets es CÓMO deciden qué nota mostrar, no
// cómo la dibujan.
sealed class NoteWidgetRow {
    data class TextRow(val text: String) : NoteWidgetRow()
    data class ImageRow(val bitmap: Bitmap) : NoteWidgetRow()
}

fun buildNoteWidgetRows(context: Context, note: Note): List<NoteWidgetRow> {
    if (note.type == NoteType.CHECKLIST) {
        return note.checklistItems.map { item ->
            NoteWidgetRow.TextRow((if (item.checked) "☑ " else "☐ ") + item.text)
        }
    }

    val parts = parseNoteContent(note.content)
    val result = mutableListOf<NoteWidgetRow>()
    var imagesShown = 0
    var imagesTotal = 0

    fun addImage(fileName: String) {
        imagesTotal++
        if (imagesShown < MAX_INLINE_IMAGES) {
            loadNoteWidgetThumbnail(context, fileName)?.let { bmp ->
                result += NoteWidgetRow.ImageRow(bmp)
                imagesShown++
            }
        }
    }

    parts.forEach { part ->
        when (part) {
            is ContentPart.TextPart -> if (part.text.isNotBlank()) result += NoteWidgetRow.TextRow(part.text)
            is ContentPart.ImagePart -> addImage(part.fileName)
            // Simplificación: se muestran todas las imágenes del grupo
            // apiladas una debajo de otra, en vez de reproducir el layout
            // real (grilla/carrusel) — no entra bien en el ancho de un
            // widget.
            is ContentPart.GalleryPart -> part.fileNames.forEach { addImage(it) }
            // Un widget no puede reproducir video ni decodificar un frame
            // para miniatura acá.
            is ContentPart.VideoPart -> result += NoteWidgetRow.TextRow(
                "🎬 " + part.caption.ifBlank { "Video" } + " — abrí la nota para verlo"
            )
        }
    }

    if (imagesTotal > MAX_INLINE_IMAGES) {
        result += NoteWidgetRow.TextRow("+ ${imagesTotal - MAX_INLINE_IMAGES} imagen(es) más — abrí la nota para verlas todas")
    }

    return result
}

fun loadNoteWidgetThumbnail(context: Context, fileName: String, maxDim: Int = 260): Bitmap? {
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

fun getNoteWidgetTextRowView(context: Context, colors: WidgetColors, noteId: Long, row: NoteWidgetRow.TextRow): RemoteViews =
    RemoteViews(context.packageName, R.layout.pinned_note_widget_text_row).apply {
        setTextViewText(R.id.RowText, row.text)
        setTextColor(R.id.RowText, colors.textSecondary)
        setOnClickFillInIntent(R.id.RowText, PinnedNoteWidgetProvider.openNoteFillInIntent(noteId))
    }

fun getNoteWidgetImageRowView(context: Context, noteId: Long, row: NoteWidgetRow.ImageRow): RemoteViews =
    RemoteViews(context.packageName, R.layout.pinned_note_widget_image_row).apply {
        setImageViewBitmap(R.id.RowImage, row.bitmap)
        setOnClickFillInIntent(R.id.RowImage, PinnedNoteWidgetProvider.openNoteFillInIntent(noteId))
    }
