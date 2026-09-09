package com.dumb.bouncynotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.dumb.bouncynotes.MainActivity
import com.dumb.bouncynotes.R

// Reloj + lista de TODAS las notas (hasta MAX_NOTES en AllNotesClockWidgetFactory).
// Sin Activity de configuración: no hay nada que elegir.
//
// HISTORIA DE ESTE ARCHIVO (para no repetir el mismo diagnóstico a medias):
// la versión anterior armaba las filas a mano con RemoteViews.addView()
// dentro de un LinearLayout simple, sin scroll, razonando que (a) ScrollView
// no está soportado dentro de un RemoteViews de widget de pantalla de
// inicio (eso es correcto, sigue siendo así) y (b) que un ListView +
// RemoteViewsFactory no serviría porque cada fila abre una nota DISTINTA y
// el bug de MIUI ya visto en PinnedNoteWidgetProvider parecía descartar ese
// mecanismo (eso NO era correcto).
//
// El bug real de MIUI (ver el comentario largo en PinnedNoteWidgetProvider)
// era específico del camino BROADCAST: setPendingIntentTemplate() con un
// PendingIntent.getBroadcast() cuyo onReceive() intentaba un startActivity()
// manual — ese salto choca con las restricciones de "Background Activity
// Launch". Pero la plantilla de setPendingIntentTemplate() puede ser
// perfectamente un PendingIntent.getActivity() (ver
// openNoteTemplatePendingIntent() más abajo): ahí el sistema arma el Intent
// final combinando la plantilla con el fill-in Intent de cada fila
// (Intent.fillIn(), documentado) y lanza la Activity él mismo, sin pasar
// por ningún onReceive() nuestro. Con esto, cada fila SÍ puede abrir una
// nota distinta (el fill-in trae el noteId) y el widget recupera el scroll
// real de un ListView nativo.
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

            // Data Uri única por widgetId: evita que el sistema confunda el
            // Intent de servicio de dos instancias de este widget entre sí
            // (mismo motivo que ya usa PinnedNoteWidgetService/Factory).
            val serviceIntent = Intent(context, AllNotesClockWidgetService::class.java).apply {
                data = Uri.parse("bouncynotes://widget/allnotesclock/$widgetId")
            }
            views.setRemoteAdapter(R.id.ListView, serviceIntent)
            views.setEmptyView(R.id.ListView, R.id.Empty)
            views.setPendingIntentTemplate(R.id.ListView, openNoteTemplatePendingIntent(context, widgetId))

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

        // Plantilla de ACTIVITY (no de broadcast) — ver el comentario largo
        // arriba de la clase y en AllNotesClockWidgetFactory.getViewAt()
        // para el porqué completo. requestCode = widgetId para que dos
        // instancias de este widget no compartan/pisen el mismo
        // PendingIntent cacheado. FLAG_MUTABLE es obligatorio en cualquier
        // PendingIntent usado como plantilla (el sistema necesita poder
        // completarlo con el fill-in Intent de cada fila).
        fun openNoteTemplatePendingIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            return PendingIntent.getActivity(
                context, widgetId, intent,
                PinnedNoteWidgetProvider.pendingIntentFlags()
            )
        }

        // Fill-in Intent (no PendingIntent): lo usa AllNotesClockWidgetFactory
        // para cada fila de la lista. Data Uri única por nota para que quede
        // claro en logs/depuración a qué nota corresponde cada combinación
        // (no es estrictamente necesaria para que el mecanismo funcione,
        // pero es el mismo criterio que ya sigue el resto del proyecto).
        fun openNoteRowFillInIntent(noteId: Long): Intent = Intent().apply {
            putExtra("openNoteId", noteId)
            data = Uri.parse("bouncynotes://widget/allnotesclock/open/$noteId")
        }
    }
}
