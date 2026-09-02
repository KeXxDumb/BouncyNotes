package com.dumb.bouncynotes.widget

import android.content.Context

// Qué nota le corresponde a cada instancia del widget (appWidgetId). Se
// usa SharedPreferences a propósito, en vez de DataStore: es lectura y
// escritura SÍNCRONA, sin ningún viaje de ida y vuelta asíncrono de por
// medio — evita por completo la clase de carrera de timing que veníamos
// arrastrando con el estado de Glance (ver historial: el widget mostraba
// "nota no disponible" hasta reconfigurar dos veces).
private const val PREFS_NAME = "pinned_note_widget_prefs"
private const val NO_NOTE = -1L

object PinnedNoteWidgetPrefs {

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setNoteId(context: Context, widgetId: Int, noteId: Long) {
        prefs(context).edit().putLong(keyFor(widgetId), noteId).apply()
    }

    fun getNoteId(context: Context, widgetId: Int): Long? =
        prefs(context).getLong(keyFor(widgetId), NO_NOTE).takeIf { it != NO_NOTE }

    fun removeWidget(context: Context, widgetId: Int) {
        prefs(context).edit().remove(keyFor(widgetId)).apply()
    }

    private fun keyFor(widgetId: Int) = "note_id_$widgetId"
}
