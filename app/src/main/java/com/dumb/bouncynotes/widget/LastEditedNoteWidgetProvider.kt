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
import com.dumb.bouncynotes.data.NoteDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class LastEditedNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> updateWidget(context, appWidgetManager, widgetId) }
    }

    companion object {

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_pinned_note)
            val colors = resolveWidgetColors(context)
            views.setInt(R.id.Layout, "setBackgroundResource", colors.backgroundRes)
            views.setTextColor(R.id.Title, colors.textPrimary)

            // Este widget no tiene Activity de configuración: siempre
            // muestra la lista (el estado "Empty" es propio del widget de
            // nota fijada, que sí necesita elegir una nota primero).
            views.setViewVisibility(R.id.HeaderRow, View.VISIBLE)
            views.setViewVisibility(R.id.ListView, View.VISIBLE)
            views.setViewVisibility(R.id.Empty, View.GONE)
            // Nada que reconfigurar acá (no elige nota, siempre la más
            // reciente sola) — a diferencia del widget de nota fijada, que
            // sí lo necesita.
            views.setViewVisibility(R.id.ChangeNote, View.GONE)

            // Mismo criterio que ya usaba el Factory antes de este fix: el
            // máximo updatedAt de verdad (dao.getAll() trae fijadas primero,
            // lo que taparía a una recién editada sin fijar).
            val current = runBlocking {
                NoteDatabase.getInstance(context).noteDao().getAll().first()
                    .filter { it.deletedAt == null && !it.isPrivate }
                    .maxByOrNull { it.updatedAt }
            }
            if (current == null) {
                views.setTextViewText(R.id.Title, "Todavía no tenés notas")
            } else {
                views.setTextViewText(R.id.Title, current.title.ifBlank { "(Sin título)" })
                // Click DIRECTO en el título — ver el comentario largo en
                // widget_pinned_note.xml (el mismo fix que en el widget de
                // nota fijada).
                views.setOnClickPendingIntent(
                    R.id.Title,
                    PinnedNoteWidgetProvider.openNotePendingIntent(context, current.id)
                )
            }

            val serviceIntent = Intent(context, LastEditedNoteWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("bouncynotes://widget/lastedited/$widgetId")
            }
            views.setRemoteAdapter(R.id.ListView, serviceIntent)

            // El ListView ya solo tiene CONTENIDO de la nota — reusa el
            // mecanismo de "abrir nota"/"tildar ítem" del widget de nota
            // fijada (PinnedNoteWidgetProvider resuelve esas actions de
            // forma genérica, sin depender de qué widget lo haya disparado)
            // — no hace falta duplicar ese receiver acá.
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
