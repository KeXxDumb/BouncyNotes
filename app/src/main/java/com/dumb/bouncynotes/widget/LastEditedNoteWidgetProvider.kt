package com.dumb.bouncynotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.dumb.bouncynotes.R

class LastEditedNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> updateWidget(context, appWidgetManager, widgetId) }
    }

    companion object {

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_pinned_note)
            val colors = resolveWidgetColors(context)
            views.setInt(R.id.Layout, "setBackgroundResource", colors.backgroundRes)

            // Este widget no tiene Activity de configuración: siempre
            // muestra la lista (el estado "Empty" es propio del widget de
            // nota fijada, que sí necesita elegir una nota primero).
            views.setViewVisibility(R.id.ListView, View.VISIBLE)
            views.setViewVisibility(R.id.Empty, View.GONE)

            val serviceIntent = Intent(context, LastEditedNoteWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("bouncynotes://widget/lastedited/$widgetId")
            }
            views.setRemoteAdapter(R.id.ListView, serviceIntent)

            // Reusa el mecanismo de "abrir nota" del widget de nota fijada
            // (PinnedNoteWidgetProvider resuelve ACTION_OPEN_NOTE de forma
            // genérica, sin depender de qué widget lo haya disparado) — no
            // hace falta duplicar ese receiver acá.
            val templateIntent = Intent(context, PinnedNoteWidgetProvider::class.java)
            val templatePendingIntent = PendingIntent.getBroadcast(
                context, widgetId, templateIntent, PinnedNoteWidgetProvider.pendingIntentFlags()
            )
            views.setPendingIntentTemplate(R.id.ListView, templatePendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.ListView)
        }

        // La llama NoteRepository después de cualquier save/delete/purgeOldTrash
        // (junto con PinnedNoteWidgetProvider.refreshAll): cualquier cambio en
        // las notas puede cambiar cuál es "la última editada".
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LastEditedNoteWidgetProvider::class.java))
            ids.forEach { widgetId -> updateWidget(context, manager, widgetId) }
        }
    }
}
