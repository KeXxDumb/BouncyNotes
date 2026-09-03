package com.dumb.bouncynotes.widget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class LastEditedNoteWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var note: Note? = null
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
        note = current
        rows = current?.let { buildNoteWidgetRows(context, it) } ?: emptyList()
        colors = resolveWidgetColors(context)
    }

    override fun getCount(): Int = 1 + rows.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position == 0) return getHeaderView()
        val noteIdForClicks = note?.id ?: 0L
        return when (val row = rows[position - 1]) {
            is NoteWidgetRow.TextRow -> getNoteWidgetTextRowView(context, colors, noteIdForClicks, row)
            is NoteWidgetRow.ImageRow -> getNoteWidgetImageRowView(context, noteIdForClicks, row)
            is NoteWidgetRow.ChecklistItemRow -> getNoteWidgetChecklistRowView(context, colors, noteIdForClicks, row)
        }
    }

    private fun getHeaderView(): RemoteViews {
        val currentNote = note
        return RemoteViews(context.packageName, R.layout.pinned_note_widget_header).apply {
            // Nada que reconfigurar en este widget (no elige nota, siempre
            // muestra la más reciente sola).
            setViewVisibility(R.id.ChangeNote, View.GONE)
            if (currentNote == null) {
                setTextViewText(R.id.Title, "Todavía no tenés notas")
            } else {
                setTextViewText(R.id.Title, currentNote.title.ifBlank { "(Sin título)" })
                setOnClickFillInIntent(R.id.Title, PinnedNoteWidgetProvider.openNoteFillInIntent(currentNote.id))
            }
            setTextColor(R.id.Title, colors.textPrimary)
        }
    }

    override fun getViewTypeCount() = 4

    override fun hasStableIds() = false

    override fun getLoadingView(): RemoteViews? = null

    override fun getItemId(position: Int): Long = position.toLong()
}
