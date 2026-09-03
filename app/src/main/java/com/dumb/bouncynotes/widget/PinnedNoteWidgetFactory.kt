package com.dumb.bouncynotes.widget

import android.content.Context
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteDatabase
import kotlinx.coroutines.runBlocking

class PinnedNoteWidgetFactory(
    private val context: Context,
    private val noteId: Long,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var note: Note? = null
    private var rows: List<NoteWidgetRow> = emptyList()
    private var colors: WidgetColors = WidgetColors(R.drawable.widget_background_light, 0, 0)

    override fun onCreate() {}

    override fun onDestroy() {}

    // Las callbacks de un RemoteViewsFactory corren en un hilo de Binder
    // dedicado del sistema, ya fuera del hilo principal — por eso está bien
    // llamar acá, de forma bloqueante, tanto a Room (con runBlocking, ya
    // que NoteDao.getById es suspend) como a BitmapFactory (dentro de
    // buildNoteWidgetRows).
    override fun onDataSetChanged() {
        val current = runBlocking { NoteDatabase.getInstance(context).noteDao().getById(noteId) }
        note = current
        rows = current?.let { buildNoteWidgetRows(context, it) } ?: emptyList()
        colors = resolveWidgetColors(context)
    }

    override fun getCount(): Int = if (note != null) 1 + rows.size else 0

    override fun getViewAt(position: Int): RemoteViews {
        if (position == 0) return getHeaderView()
        return when (val row = rows[position - 1]) {
            is NoteWidgetRow.TextRow -> getNoteWidgetTextRowView(context, colors, noteId, row)
            is NoteWidgetRow.ImageRow -> getNoteWidgetImageRowView(context, noteId, row)
            is NoteWidgetRow.ChecklistItemRow -> getNoteWidgetChecklistRowView(context, colors, noteId, row)
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

    override fun getViewTypeCount() = 4

    override fun hasStableIds() = false

    override fun getLoadingView(): RemoteViews? = null

    override fun getItemId(position: Int): Long = position.toLong()
}
