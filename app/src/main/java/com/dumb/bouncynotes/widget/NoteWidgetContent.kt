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
// texto, ítem de checklist tildable, o una imagen ya decodificada y
// reducida. Compartida entre el widget de "nota fijada" y el de "última
// nota editada" — la única diferencia entre esos dos widgets es CÓMO
// deciden qué nota mostrar, no cómo la dibujan. Si la nota es de tipo
// checklist, CUALQUIERA de los dos "muta" solo a una lista tildable — no
// hace falta un tercer widget aparte para eso.
sealed class NoteWidgetRow {
    data class TextRow(val text: String) : NoteWidgetRow()
    data class ImageRow(val bitmap: Bitmap) : NoteWidgetRow()
    data class ChecklistItemRow(val itemIndex: Int, val text: String, val checked: Boolean) : NoteWidgetRow()
}

fun buildNoteWidgetRows(context: Context, note: Note): List<NoteWidgetRow> {
    if (note.type == NoteType.CHECKLIST) {
        return note.checklistItems.mapIndexed { index, item ->
            NoteWidgetRow.ChecklistItemRow(index, item.text, item.checked)
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

fun getNoteWidgetChecklistRowView(
    context: Context,
    colors: WidgetColors,
    noteId: Long,
    row: NoteWidgetRow.ChecklistItemRow
): RemoteViews =
    RemoteViews(context.packageName, R.layout.pinned_note_widget_checklist_row).apply {
        setTextViewText(R.id.CheckboxGlyph, if (row.checked) "☑" else "☐")
        setTextColor(R.id.CheckboxGlyph, colors.textPrimary)
        setTextViewText(R.id.ChecklistText, row.text)
        setTextColor(R.id.ChecklistText, colors.textSecondary)
        // CheckboxGlyph y ChecklistText son HERMANOS (mismo nivel, sin
        // superponerse) — no un contenedor + un hijo — por la misma razón
        // que Title/ChangeNote en el encabezado: dos vistas ANIDADAS con
        // manejadores de click distintos es un problema conocido en listas
        // de widgets.
        setOnClickFillInIntent(
            R.id.CheckboxGlyph,
            PinnedNoteWidgetProvider.toggleChecklistItemFillInIntent(noteId, row.itemIndex)
        )
        setOnClickFillInIntent(R.id.ChecklistText, PinnedNoteWidgetProvider.openNoteFillInIntent(noteId))
    }
