package com.dumb.bouncynotes.widget

import android.content.Context
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.NoteDatabase
import kotlinx.coroutines.runBlocking

class PinnedNoteWidgetFactory(
    private val context: Context,
    private val noteId: Long,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

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
        rows = current?.let { buildNoteWidgetRows(context, it) } ?: emptyList()
        colors = resolveWidgetColors(context)
    }

    // Ya NO incluye una fila de header (ver widget_pinned_note.xml): el
    // título vive ahora en una vista fija fuera de este ListView, con su
    // propio click directo — acá solo queda el CONTENIDO de la nota.
    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        return when (val row = rows[position]) {
            is NoteWidgetRow.TextRow -> getNoteWidgetTextRowView(context, colors, noteId, row)
            is NoteWidgetRow.ImageRow -> getNoteWidgetImageRowView(context, noteId, row)
            is NoteWidgetRow.ChecklistItemRow -> getNoteWidgetChecklistRowView(context, colors, noteId, row)
        }
    }

    override fun getViewTypeCount() = 3

    override fun hasStableIds() = false

    override fun getLoadingView(): RemoteViews? = null

    override fun getItemId(position: Int): Long = position.toLong()
}
