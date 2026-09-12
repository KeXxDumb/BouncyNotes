package com.dumb.bouncynotes.widget

import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.NoteDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class LastEditedNoteWidgetFactory(private val context: Context, private val widgetId: Int) : RemoteViewsService.RemoteViewsFactory {

    // Antes `noteId` y `rows` eran dos `var` SEPARADOS, actualizados juntos
    // en onDataSetChanged() pero sin ninguna garantía real de quedar
    // siempre sincronizados entre sí (a diferencia de PinnedNoteWidgetFactory,
    // donde noteId es un parámetro del constructor, fijo desde el vamos, sin
    // ventana posible de desincronización). Si getViewAt() llegara a leer
    // `noteId` en su valor inicial (0L) mientras `rows` ya tenía contenido
    // de una nota real, cada fila terminaría abriendo la nota "0" — que
    // MainActivity filtra explícitamente como "no hay nada que abrir"
    // (openNoteId == 0L se descarta ahí a propósito). Agruparlos en un solo
    // estado atómico, reemplazado de una sola vez, elimina esa ventana.
    private data class LoadedNote(val noteId: Long, val rows: List<NoteWidgetRow>)

    private var loaded: LoadedNote = LoadedNote(0L, emptyList())
    private var colors: WidgetColors = WidgetColors(R.drawable.widget_background_light, 0, 0, 0, 0, WidgetBackgroundMode.SOLID)

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
        loaded = if (current != null) {
            LoadedNote(current.id, buildNoteWidgetRows(context, current))
        } else {
            LoadedNote(0L, emptyList())
        }
        colors = resolveWidgetColors(context, widgetId)
        Log.d("BouncyNotesWidget", "LastEditedNoteWidgetFactory.onDataSetChanged() noteId=${loaded.noteId} filas=${loaded.rows.size}")
    }

    // Ya NO incluye una fila de header (ver widget_pinned_note.xml): el
    // título lo pone LastEditedNoteWidgetProvider directo en la vista fija
    // de arriba — acá solo queda el CONTENIDO de la nota.
    override fun getCount(): Int = loaded.rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val noteId = loaded.noteId
        Log.d("BouncyNotesWidget", "LastEditedNoteWidgetFactory.getViewAt($position) noteId=$noteId tipo=${loaded.rows[position]::class.simpleName}")
        return when (val row = loaded.rows[position]) {
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
