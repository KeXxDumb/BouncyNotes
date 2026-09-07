package com.dumb.bouncynotes.widget

import android.content.Context
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.parseNoteContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// Límite pedido: como mucho 25 notas en la lista, sin importar cuántas
// tenga el usuario en total (un widget viaja por Binder con un límite chico
// de tamaño total — 25 filas de una sola línea de texto entra sin problema,
// pero no tendría sentido (ni entraría bien) intentar listarlas TODAS si
// hay cientos).
private const val MAX_NOTES = 25

class AllNotesClockWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<Note> = emptyList()
    private var colors: WidgetColors = WidgetColors(R.drawable.widget_background_light, 0, 0)

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
        colors = resolveWidgetColors(context)
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        val note = notes[position]
        return RemoteViews(context.packageName, R.layout.widget_note_summary_row).apply {
            setTextViewText(R.id.RowTitle, (if (note.pinned) "📌 " else "") + note.title.ifBlank { "(Sin título)" })
            setTextColor(R.id.RowTitle, colors.textPrimary)
            setTextViewText(R.id.RowPreview, notePreviewLine(note))
            setTextColor(R.id.RowPreview, colors.textSecondary)
            setOnClickFillInIntent(R.id.Row, PinnedNoteWidgetProvider.openNoteFillInIntent(note.id))
        }
    }

    override fun getViewTypeCount() = 1

    // Antes esto era `true` con notes[position].id como id — el ÚNICO de
    // los widgets con lista que usaba ids estables (los demás, con una sola
    // nota fija, usan `false` + position.toLong()). Cada fila acá solo
    // tiene UN objetivo de click (a diferencia del bug del checklist, que
    // tenía dos), así que esta diferencia — ids estables — quedó como la
    // única variable real distinta entre "esto no abre nada" (esta lista) y
    // "esto sí abre" (una fila de texto/imagen suelta). Se iguala al mismo
    // criterio que ya usan los otros factories, por las dudas.
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
        return if (line.isNullOrEmpty()) "(Sin contenido de texto)" else line
    }
}
