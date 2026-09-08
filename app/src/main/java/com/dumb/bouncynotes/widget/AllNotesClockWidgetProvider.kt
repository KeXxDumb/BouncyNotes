package com.dumb.bouncynotes.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.parseNoteContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// Límite pedido: como mucho 25 notas en la lista, sin importar cuántas
// tenga el usuario en total (un widget viaja por Binder con un límite chico
// de tamaño total — 25 filas de una sola línea de texto entra sin problema,
// pero no tendría sentido intentar listarlas TODAS si hay cientos).
private const val MAX_NOTES = 25

// Reloj + lista de TODAS las notas (hasta 25). Sin Activity de
// configuración: no hay nada que elegir.
//
// BUG (reportado, Xiaomi/MIUI, confirmado con logcat): tocar una fila no
// abría nada — "sendActivityPendingIntent() .send() OK" sin excepción,
// pero nunca un MainActivity.onCreate()/onNewIntent() después. Encaja con
// las restricciones de "Background Activity Launch", más estrictas para
// un PendingIntent de BROADCAST que dispara startActivity() dentro de
// onReceive() (lo que exigía el ListView + RemoteViewsFactory de antes)
// que para un PendingIntent de Activity DIRECTO.
//
// A diferencia de "Nota fijada"/"Última nota editada" (siempre la MISMA
// nota, así que alcanzaba un único PendingIntent directo como plantilla de
// todo el ListView), acá cada fila abre una nota DISTINTA — no existe una
// plantilla única posible para eso. La solución real: dejar de usar
// ListView/RemoteViewsFactory del todo. Las filas se agregan a mano
// (RemoteViews.addView(), ver abajo) dentro de un LinearLayout envuelto en
// ScrollView (ver widget_all_notes_clock.xml) — al no ser elementos de un
// adaptador, cada una SÍ puede tener su propio click directo, sin ningún
// broadcast de por medio. El costo: ya no hay carga perezosa (las ~25
// notas se arman todas de una), pero para filas de texto simple eso no
// pesa nada real.
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

            // dao.getAll() ya viene ordenado "fijadas primero, después por
            // updatedAt" (mismo orden que la lista de la app).
            val notes = runBlocking {
                NoteDatabase.getInstance(context).noteDao().getAll().first()
                    .filter { it.deletedAt == null && !it.isPrivate }
                    .take(MAX_NOTES)
            }
            Log.d("BouncyNotesWidget", "AllNotesClockWidgetProvider notas=${notes.size} ids=${notes.map { it.id }}")

            views.removeAllViews(R.id.RowsContainer)
            if (notes.isEmpty()) {
                views.setViewVisibility(R.id.Empty, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.Empty, View.GONE)
                notes.forEach { note ->
                    val row = RemoteViews(context.packageName, R.layout.widget_note_summary_row).apply {
                        setTextViewText(R.id.RowTitle, (if (note.pinned) "📌 " else "") + note.title.ifBlank { "(Sin título)" })
                        setTextColor(R.id.RowTitle, colors.textPrimary)
                        setTextViewText(R.id.RowPreview, notePreviewLine(note))
                        setTextColor(R.id.RowPreview, colors.textSecondary)
                        // Click DIRECTO — ver el comentario largo arriba de
                        // la clase sobre por qué esto es justo lo que
                        // arregla el bug de Xiaomi/MIUI.
                        setOnClickPendingIntent(R.id.Row, PinnedNoteWidgetProvider.openNotePendingIntent(context, note.id))
                    }
                    views.addView(R.id.RowsContainer, row)
                }
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        // La llama NoteRepository después de cualquier save/delete/purgeOldTrash
        // (junto con los otros dos widgets con lista): cualquier cambio en
        // las notas puede afectar esta lista completa.
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AllNotesClockWidgetProvider::class.java))
            ids.forEach { widgetId -> updateWidget(context, manager, widgetId) }
        }

        // Una línea de preview simple, en texto plano — no reproduce el
        // preview "de verdad" de la lista de la app (NotePreviewContent, un
        // @Composable, no usable acá) a propósito: alcanza con dar una idea
        // del contenido para decidir si tocar la fila.
        private fun notePreviewLine(note: Note): String {
            if (note.type == NoteType.CHECKLIST) {
                val total = note.checklistItems.size
                val checked = note.checklistItems.count { it.checked }
                return if (total == 0) "Checklist vacío" else "$checked/$total marcados"
            }
            val firstText = parseNoteContent(note.content)
                .filterIsInstance<ContentPart.TextPart>()
                .map { it.text.trim() }
                .firstOrNull { it.isNotEmpty() }
            val line = firstText?.lineSequence()?.firstOrNull()?.trim()
            return if (line.isNullOrEmpty()) "(Sin contenido de texto)" else line
        }
    }
}
