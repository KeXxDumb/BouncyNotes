package com.dumb.bouncynotes.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.parseNoteContent
import com.dumb.bouncynotes.data.stripFormattingMarkers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// Límite pedido: como mucho 25 notas en la lista, sin importar cuántas
// tenga el usuario en total (un widget viaja por Binder con un límite chico
// de tamaño total — 25 filas de una sola línea de texto entra sin problema,
// pero no tendría sentido intentar listarlas TODAS si hay cientos).
private const val MAX_NOTES = 25

class AllNotesClockWidgetFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<Note> = emptyList()
    private var colors: WidgetColors = WidgetColors(R.drawable.widget_background_light, 0, 0, 0, 0, WidgetBackgroundMode.SOLID)

    override fun onCreate() {}

    override fun onDestroy() {}

    override fun onDataSetChanged() {
        // dao.getAll() ya viene ordenado "fijadas primero, después por
        // updatedAt" (mismo orden que la lista de la app) — acá sí sirve tal
        // cual, a diferencia de LastEditedNoteWidgetFactory: no buscamos LA
        // más reciente sola, sino un listado completo, y ese es justamente
        // el orden esperado para un listado.
        notes = runBlocking {
            NoteDatabase.getInstance(context).noteDao().getAll().first()
                .filter { it.deletedAt == null && !it.isPrivate }
                .take(MAX_NOTES)
        }
        colors = resolveWidgetColors(context, widgetId)
        Log.d("BouncyNotesWidget", "AllNotesClockWidgetFactory.onDataSetChanged() notas=${notes.size} ids=${notes.map { it.id }}")
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        val note = notes[position]
        return RemoteViews(context.packageName, R.layout.widget_note_summary_row).apply {
            setTextViewText(R.id.RowTitle, note.title.ifBlank { "(Sin título)" })
            setTextColor(R.id.RowTitle, colors.textPrimary)
            // Ícono vectorial en vez del emoji 📌: un ImageView aparte (los
            // spans con drawables no viajan por RemoteViews). Los RemoteViews
            // se reciclan, así que hay que fijar la visibilidad en AMBOS casos.
            setViewVisibility(R.id.RowPin, if (note.pinned) View.VISIBLE else View.GONE)
            if (note.pinned) setInt(R.id.RowPin, "setColorFilter", colors.textPrimary)
            setTextViewText(R.id.RowPreview, notePreviewLine(note))
            setTextColor(R.id.RowPreview, colors.textSecondary)
            // Fill-in intent SOLO con extras (sin action/data/component) —
            // ver el comentario grande en AllNotesClockWidgetProvider sobre
            // por qué esto alcanza para que la plantilla de Activity
            // directa abra la nota correcta de cada fila.
            setOnClickFillInIntent(R.id.Row, Intent().putExtra("openNoteId", note.id))
        }
    }

    override fun getViewTypeCount() = 1

    override fun hasStableIds() = false

    override fun getLoadingView(): RemoteViews? = null

    override fun getItemId(position: Int): Long = position.toLong()

    // Una línea de preview simple, en texto plano — no reproduce el preview
    // "de verdad" de la lista de la app (NotePreviewContent, un
    // @Composable, no usable acá) a propósito: alcanza con dar una idea del
    // contenido para decidir si tocar la fila.
    private fun notePreviewLine(note: Note): String {
        if (note.type == NoteType.CHECKLIST) {
            val total = note.checklistItems.size
            val checked = note.checklistItems.count { it.checked }
            return if (total == 0) "Checklist vacío" else "$checked/$total marcados"
        }
        val firstText = parseNoteContent(note.content)
            .filterIsInstance<ContentPart.TextPart>()
            .map { it.text.trim() }
            .firstOrNull { it.isNotEmpty() }
        val line = firstText?.lineSequence()?.firstOrNull()?.trim()
        // Línea de una sola línea, truncada por la propia fila del widget:
        // no vale la pena parsear los marcadores a spans acá (como sí hace
        // buildInlineSpannable en las filas completas de los otros dos
        // widgets) — alcanza con no mostrar los asteriscos/tildes crudos.
        return if (line.isNullOrEmpty()) "(Sin contenido de texto)" else stripFormattingMarkers(line)
    }
}
