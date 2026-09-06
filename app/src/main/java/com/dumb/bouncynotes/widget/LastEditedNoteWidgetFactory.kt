package com.dumb.bouncynotes.widget

import android.content.Context
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.NoteDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class LastEditedNoteWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var noteId: Long = 0L
    private var rows: List<NoteWidgetRow> = emptyList()
    private var colors: WidgetColors = WidgetColors(R.drawable.widget_background_light, 0, 0)

    override fun onCreate() {}

    override fun onDestroy() {}

    override fun onDataSetChanged() {
        // dao.getAll() ya viene ordenado "fijadas primero, después por
        // updatedAt" (mismo orden que usa la lista de la app) — acá NO
        // sirve, porque una nota fijada vieja taparía a una recién editada
        // sin fijar. Se necesita el máximo updatedAt de verdad, sin
        // importar si está fijada.
        val current = runBlocking {
            NoteDatabase.getInstance(context).noteDao().getAll().first()
                .filter { it.deletedAt == null && !it.isPrivate }
                .maxByOrNull { it.updatedAt }
        }
        noteId = current?.id ?: 0L
        rows = current?.let { buildNoteWidgetRows(context, it) } ?: emptyList()
        colors = resolveWidgetColors(context)
    }

    // Ya NO incluye una fila de header (ver widget_pinned_note.xml): el
    // título lo pone LastEditedNoteWidgetProvider directo en la vista fija
    // de arriba — acá solo queda el CONTENIDO de la nota.
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
