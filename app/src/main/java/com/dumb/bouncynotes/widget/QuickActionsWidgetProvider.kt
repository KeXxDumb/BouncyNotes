package com.dumb.bouncynotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dumb.bouncynotes.MainActivity
import com.dumb.bouncynotes.R

class QuickActionsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> updateWidget(context, appWidgetManager, widgetId) }
    }

    companion object {

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)
            val colors = resolveWidgetColors(context)
            views.setInt(R.id.Layout, "setBackgroundResource", colors.backgroundRes)
            views.setTextColor(R.id.Clock, colors.textPrimary)
            views.setTextColor(R.id.NewNoteLabel, colors.textPrimary)
            views.setTextColor(R.id.NewChecklistLabel, colors.textPrimary)

            views.setOnClickPendingIntent(R.id.NewNoteButton, newNotePendingIntent(context, widgetId, "TEXT"))
            views.setOnClickPendingIntent(R.id.NewChecklistButton, newNotePendingIntent(context, widgetId, "CHECKLIST"))

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        // No hay lista acá (ni RemoteViewsService/Factory): son solo dos
        // botones fijos, así que un PendingIntent.getActivity() directo por
        // botón alcanza — no hace falta el mecanismo de plantilla+fill-in
        // que sí necesitan los otros dos widgets (nota fijada / última
        // editada), que muestran una LISTA dinámica de filas.
        private fun newNotePendingIntent(context: Context, widgetId: Int, type: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("newNoteType", type)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            // Los dos botones de UN MISMO widget apuntan al mismo componente
            // (MainActivity) sin action/data propios — solo difieren en un
            // extra, que NO forma parte de la identidad de un PendingIntent.
            // Sin requestCodes distintos, el segundo botón pisaría al
            // primero (mismo PendingIntent, ambos terminarían abriendo el
            // mismo tipo de nota).
            val requestCode = widgetId * 2 + if (type == "TEXT") 0 else 1
            return PendingIntent.getActivity(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
