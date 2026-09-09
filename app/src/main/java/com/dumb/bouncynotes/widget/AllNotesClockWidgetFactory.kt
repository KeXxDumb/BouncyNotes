package com.dumb.bouncynotes.widget

import android.content.Context
import android.util.Log
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

// Mismo límite que tenía la versión anterior armada a mano (ver
// AllNotesClockWidgetProvider): 25 notas como mucho.
private const val MAX_NOTES = 25

class AllNotesClockWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var notes: List<Note> = emptyList()
    private var colors: WidgetColors = WidgetColors(R.drawable.widget_background_light, 0, 0)

    override fun onCreate() {}

    override fun onDestroy() {}

    override fun onDataSetChanged() {
        // dao.getAll() ya viene ordenado "fijadas primero, después por
        // updatedAt" (mismo orden que la lista de la app) — acá sí sirve,
        // a diferencia de LastEditedNoteWidgetFactory, porque esta lista
        // quiere mostrar TODAS las notas con ese criterio, no solo "la más
        // reciente sea o no fijada".
        notes = runBlocking {
            NoteDatabase.getInstance(context).noteDao().getAll().first()
                .filter { it.deletedAt == null && !it.isPrivate }
                .take(MAX_NOTES)
        }
        colors = resolveWidgetColors(context)
        Log.d("BouncyNotesWidget", "AllNotesClockWidgetFactory.onDataSetChanged() notas=${notes.size} ids=${notes.map { it.id }}")
    }

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews {
        val note = notes[position]
        return RemoteViews(context.packageName, R.layout.widget_note_summary_row).apply {
            setTextViewText(R.id.RowTitle, (if (note.pinned) "📌 " else "") + note.title.ifBlank { "(Sin título)" })
            setTextColor(R.id.RowTitle, colors.textPrimary)
            setTextViewText(R.id.RowPreview, notePreviewLine(note))
            setTextColor(R.id.RowPreview, colors.textSecondary)
            // Un ListView respaldado por RemoteViewsFactory NO admite
            // setOnClickPendingIntent en sus filas (documentado) — hace
            // falta el mecanismo de "plantilla + fill-in Intent": cada fila
            // aporta acá su propio noteId + data Uri únicos, y el SISTEMA
            // los combina (Intent.fillIn()) con la plantilla puesta en
            // AllNotesClockWidgetProvider.openNoteTemplatePendingIntent()
            // recién al tocar la fila.
            //
            // Punto clave que el intento anterior de este widget pasó por
            // alto: esa plantilla puede ser un PendingIntent.getActivity()
            // apuntando directo a MainActivity (como acá), no hace falta
            // que sea un PendingIntent.getBroadcast() — el sistema arma y
            // lanza el Intent final por su cuenta, sin pasar por ningún
            // onReceive() propio nuestro. El bug de MIUI documentado en
            // PinnedNoteWidgetProvider (tocar una fila no abría nada) era
            // específico del camino BROADCAST → onReceive() → startActivity()
            // manual (sujeto a las restricciones de "Background Activity
            // Launch"), no del mecanismo de plantilla+fill-in en sí mismo.
            // Con una plantilla de Activity, cada fila puede seguir abriendo
            // una nota DISTINTA sin ese riesgo.
            setOnClickFillInIntent(R.id.Row, AllNotesClockWidgetProvider.openNoteRowFillInIntent(note.id))
        }
    }

    override fun getViewTypeCount() = 1

    override fun hasStableIds() = false

    override fun getLoadingView(): RemoteViews? = null

    override fun getItemId(position: Int): Long = position.toLong()

    // Idéntica a la que tenía la versión anterior armada a mano.
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
