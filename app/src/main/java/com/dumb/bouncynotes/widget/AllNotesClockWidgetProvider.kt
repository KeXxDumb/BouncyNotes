package com.dumb.bouncynotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.dumb.bouncynotes.R

// Reloj + lista scrolleable de TODAS las notas (hasta 25). Sin Activity de
// configuración, igual que LastEditedNoteWidgetProvider: no hay nada que
// elegir. El único trabajo de este provider es armar el layout raíz (reloj +
// ListView) y apuntar el ListView al RemoteViewsService correspondiente —
// abrir cada nota lo resuelve PinnedNoteWidgetProvider de forma genérica,
// reusando exactamente el mismo mecanismo que ya usan los otros dos widgets
// con lista (ver el comentario de LastEditedNoteWidgetProvider al respecto).
class AllNotesClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> updateWidget(context, appWidgetManager, widgetId) }
    }

    companion object {

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_all_notes_clock)
            val colors = resolveWidgetColors(context)
            views.setInt(R.id.Layout, "setBackgroundResource", colors.backgroundRes)
            views.setTextColor(R.id.Clock, colors.textPrimary)
            views.setTextColor(R.id.Empty, colors.textSecondary)

            val serviceIntent = Intent(context, AllNotesClockWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("bouncynotes://widget/allnotes/$widgetId")
            }
            views.setRemoteAdapter(R.id.ListView, serviceIntent)
            views.setEmptyView(R.id.ListView, R.id.Empty)

            // Misma plantilla sin action fija que ya usan los otros widgets
            // con lista: cada fila trae su propio fill-in Intent (abrir esa
            // nota puntual) desde el Factory.
            val templateIntent = Intent(context, PinnedNoteWidgetProvider::class.java)
            val templatePendingIntent = PendingIntent.getBroadcast(
                context, widgetId, templateIntent, PinnedNoteWidgetProvider.pendingIntentFlags()
            )
            views.setPendingIntentTemplate(R.id.ListView, templatePendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.ListView)
        }

        // La llama NoteRepository después de cualquier save/delete/purgeOldTrash
        // (junto con los otros dos widgets con lista): cualquier cambio en
        // las notas puede afectar esta lista completa.
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AllNotesClockWidgetProvider::class.java))
            ids.forEach { widgetId -> updateWidget(context, manager, widgetId) }
        }
    }
}
