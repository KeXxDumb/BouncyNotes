package com.dumb.bouncynotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.widget.RemoteViews
import com.dumb.bouncynotes.MainActivity
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
            // Las tres tarjetas redondeadas (lista, reloj, recordatorio),
            // más oscuras que el fondo del widget — ver
            // widget_all_notes_clock.xml.
            views.setInt(R.id.NotesTile, "setBackgroundResource", colors.tileBackgroundRes)
            views.setInt(R.id.Clock, "setBackgroundResource", colors.tileBackgroundRes)
            views.setInt(R.id.ReminderBox, "setBackgroundResource", colors.tileBackgroundRes)
            views.setTextColor(R.id.ReminderLabel, colors.textSecondary)
            views.setTextColor(R.id.ReminderValue, colors.textPrimary)

            // Recuadro de "próximo recordatorio" (rediseño a partir de un
            // boceto del usuario) — a diferencia de la lista de la
            // izquierda, esto NO es una fila más del Factory: es un solo
            // dato fijo (el recordatorio más próximo entre TODAS las
            // notas), así que se calcula acá derecho, igual que ya hace
            // LastEditedNoteWidgetProvider con "la nota más reciente".
            // Consulta por Room prácticamente instantánea — un runBlocking
            // puntual acá no es problema real (mismo criterio ya usado en
            // los otros providers).
            val notes = runBlocking {
                NoteDatabase.getInstance(context).noteDao().getAll().first()
                    .filter { it.deletedAt == null && !it.isPrivate }
            }
            val now = System.currentTimeMillis()
            val nextReminder = notes
                .mapNotNull { note -> ReminderScheduler.nextTrigger(note)?.let { note to it.triggerAt } }
                .filter { (_, triggerAt) -> triggerAt > now }
                .minByOrNull { (_, triggerAt) -> triggerAt }

            if (nextReminder != null) {
                val (note, triggerAt) = nextReminder
                views.setTextViewText(
                    R.id.ReminderValue,
                    "${formatReminderWhen(context, triggerAt)} · ${note.title.ifBlank { "(Sin título)" }}"
                )
                views.setOnClickPendingIntent(R.id.ReminderBox, PinnedNoteWidgetProvider.openNotePendingIntent(context, note.id))
            } else {
                views.setTextViewText(R.id.ReminderValue, "Sin recordatorios próximos")
                // Sin click: no hay ninguna nota puntual a la que llevar al
                // tocar acá (ver el comentario en el layout).
            }

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

        // "Hoy 9:00 AM" / "Mañana 9:00 AM" / "Vie 9:00 AM" — formato chico a
        // propósito, para el recuadro angosto de la derecha. La HORA respeta
        // el formato de 12/24hs del sistema (DateFormat.getTimeFormat, la
        // misma fuente que ya usa el propio TextClock con
        // format12Hour/format24Hour) — no se fuerza un formato fijo.
        //
        // diffDays: RESTA primero los dos "inicio de día" en millis y recién
        // DESPUÉS divide (con Math.round, no división entera) — no divide
        // cada uno por separado y resta los resultados. Con un huso horario
        // de offset no entero (hay unos cuantos de 30/45 minutos) esas dos
        // formas NO dan siempre el mismo resultado; esta es la misma que ya
        // se usa en ReminderPickerSheet.kt (formatNextTrigger) por el mismo
        // motivo.
        private fun formatReminderWhen(context: Context, triggerAt: Long): String {
            fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
                timeInMillis = millis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val dayMillis = 24L * 60L * 60L * 1000L
            val diffDays = Math.round((startOfDay(triggerAt) - startOfDay(System.currentTimeMillis())) / dayMillis.toDouble())
            val dayPart = when (diffDays) {
                0L -> "Hoy"
                1L -> "Mañana"
                else -> java.text.SimpleDateFormat("EEE", Locale("es")).format(Date(triggerAt)).replaceFirstChar { it.uppercase() }
            }
            val timePart = DateFormat.getTimeFormat(context).format(Date(triggerAt))
            return "$dayPart $timePart"
        }
    }
}
