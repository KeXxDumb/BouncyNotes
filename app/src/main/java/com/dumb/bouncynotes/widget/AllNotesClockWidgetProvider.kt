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

// Reloj + lista scrolleable de TODAS las notas (hasta 25). Sin Activity de
// configuración para elegir NOTA (no hay nada que elegir, siempre son
// todas) — pero SÍ tiene una para la apariencia (tema/fondo), ver
// WidgetAppearanceConfigActivity.
//
// BUG YA RESUELTO (Xiaomi/MIUI, confirmado con logcat): tocar una fila no
// abría nada — "sendActivityPendingIntent() .send() OK" sin excepción,
// pero nunca un MainActivity.onCreate()/onNewIntent() después. Encajaba
// con las restricciones de "Background Activity Launch", más estrictas
// para un PendingIntent de BROADCAST que dispara startActivity() dentro de
// onReceive() que para uno de Activity DIRECTO.
//
// Se probó sacar el ListView del todo (filas fijas con click directo cada
// una) para evitar el broadcast, pero eso también sacaba el scroll de
// verdad (ScrollView no está permitido en RemoteViews para widgets de
// pantalla de inicio) — quedaba sin poder scrollear la lista.
//
// La solución real, que da las DOS cosas (scroll de verdad Y clicks
// confiables): la plantilla del ListView (setPendingIntentTemplate) puede
// ser un PendingIntent.getActivity() DIRECTO (no getBroadcast()) siempre
// que la única diferencia entre filas sean EXTRAS del Intent — porque
// Intent.fillIn() sí combina extras del fill-in de cada fila con el
// Intent de la plantilla (a diferencia de action/data/component, que la
// plantilla ya trae fijos y fillIn() no pisa). Como cada fila solo necesita
// avisar "abrí ESTA nota" (un extra más, openNoteId), no hace falta que la
// plantilla en sí sea distinta por fila — sigue siendo, en el fondo, la
// MISMA clase de click directo que ya funciona de forma confiable en el
// título de "Nota fijada"/"Última nota editada", sin pasar por ningún
// receiver ni broadcast.
class AllNotesClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> updateWidget(context, appWidgetManager, widgetId) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> WidgetAppearancePrefs.removeWidget(context, widgetId) }
    }

    companion object {

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_all_notes_clock)
            val colors = resolveWidgetColors(context, widgetId)
            applyWidgetBackground(views, R.id.Layout, colors)
            views.setTextColor(R.id.Clock, colors.textPrimary)
            views.setTextColor(R.id.Empty, colors.textSecondary)
            views.setInt(R.id.HeaderDivider, "setBackgroundColor", colors.divider)

            val serviceIntent = Intent(context, AllNotesClockWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("bouncynotes://widget/allnotes/$widgetId")
            }
            views.setRemoteAdapter(R.id.ListView, serviceIntent)
            views.setEmptyView(R.id.ListView, R.id.Empty)

            // Plantilla de Activity DIRECTA — ver el comentario grande
            // arriba de la clase. Sin openNoteId puesto acá: cada fila lo
            // suma como extra vía su propio fill-in Intent
            // (AllNotesClockWidgetFactory.getViewAt).
            val openIntentTemplate = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                // Identidad única por INSTANCIA de widget (no por nota, acá
                // varía por fila) — alcanza con el propio widgetId.
                data = Uri.parse("bouncynotes://widget/allnotes/mainactivity/$widgetId")
            }
            val templatePendingIntent = PendingIntent.getActivity(
                context, widgetId, openIntentTemplate,
                // BUG (reportado): tocar una fila solo abría la app, sin ir
                // a la nota específica. Causa real: una plantilla de
                // ListView (setPendingIntentTemplate) necesita
                // FLAG_MUTABLE, no FLAG_IMMUTABLE — con IMMUTABLE el
                // sistema no puede completarla con el fill-in Intent de
                // cada fila (el openNoteId), así que se disparaba la
                // plantilla base tal cual, sin ningún dato agregado. Los
                // clicks DIRECTOS de este proyecto (Title, ChangeNote,
                // etc., que nunca se completan con nada más) sí usan
                // IMMUTABLE correctamente — esto era la única plantilla
                // real que necesitaba MUTABLE.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
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
