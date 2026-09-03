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
import com.dumb.bouncynotes.MainActivity
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val ACTION_OPEN_NOTE = "com.dumb.bouncynotes.widget.ACTION_OPEN_NOTE"
private const val ACTION_RECONFIGURE = "com.dumb.bouncynotes.widget.ACTION_RECONFIGURE"
private const val ACTION_TOGGLE_CHECKLIST_ITEM = "com.dumb.bouncynotes.widget.ACTION_TOGGLE_CHECKLIST_ITEM"
const val EXTRA_NOTE_ID = "com.dumb.bouncynotes.widget.EXTRA_NOTE_ID"
private const val EXTRA_ITEM_INDEX = "com.dumb.bouncynotes.widget.EXTRA_ITEM_INDEX"

class PinnedNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> updateWidget(context, appWidgetManager, widgetId) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> PinnedNoteWidgetPrefs.removeWidget(context, widgetId) }
    }

    // Los clicks dentro de la lista del widget (ListView + RemoteViewsService)
    // viajan como BROADCASTS hacia este mismo provider — setPendingIntentTemplate
    // exige específicamente un PendingIntent.getBroadcast(), no uno de Activity.
    // Por eso "abrir la nota" y "reconfigurar" se resuelven acá, no directo
    // desde el Factory.
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_OPEN_NOTE -> {
                val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("openNoteId", noteId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(openIntent)
            }
            ACTION_RECONFIGURE -> {
                val widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val configIntent = Intent(context, PinnedNoteWidgetConfigActivity::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(configIntent)
                }
            }
            ACTION_TOGGLE_CHECKLIST_ITEM -> {
                val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
                val itemIndex = intent.getIntExtra(EXTRA_ITEM_INDEX, -1)
                if (noteId != 0L && itemIndex >= 0) {
                    // Tocar Room desde onReceive necesita salirse del hilo
                    // principal, pero el receiver puede destruirse apenas
                    // onReceive() retorna — goAsync() lo mantiene vivo hasta
                    // que la corrutina llame a pendingResult.finish().
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            toggleChecklistItem(context, noteId, itemIndex)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    private suspend fun toggleChecklistItem(context: Context, noteId: Long, itemIndex: Int) {
        val dao = NoteDatabase.getInstance(context).noteDao()
        val note = dao.getById(noteId) ?: return
        if (itemIndex !in note.checklistItems.indices) return
        val updatedItems = note.checklistItems.toMutableList().apply {
            this[itemIndex] = this[itemIndex].copy(checked = !this[itemIndex].checked)
        }
        // Pasa por NoteRepository (no dao.upsert directo) para que dispare
        // el mismo refresh que cualquier otro guardado: así se actualiza
        // ESTE widget y también el de "última nota editada", si esta nota
        // fuera además la más reciente.
        NoteRepository(dao, context).save(
            note.copy(checklistItems = updatedItems, updatedAt = System.currentTimeMillis())
        )
    }

    companion object {

        // La llama PinnedNoteWidgetConfigActivity al terminar de elegir una
        // nota, y el propio onUpdate() del sistema para cada widget.
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val noteId = PinnedNoteWidgetPrefs.getNoteId(context, widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_pinned_note)

            // Un widget no puede usar el theming de Compose de la app —
            // se resuelve claro/oscuro a mano, con el mismo criterio que
            // ya usa MainActivity, y se aplica color por color.
            val colors = resolveWidgetColors(context)
            views.setInt(R.id.Layout, "setBackgroundResource", colors.backgroundRes)
            views.setTextColor(R.id.Empty, colors.textSecondary)

            if (noteId == null) {
                views.setViewVisibility(R.id.ListView, View.GONE)
                views.setViewVisibility(R.id.Empty, View.VISIBLE)
                views.setOnClickPendingIntent(R.id.Empty, reconfigurePendingIntent(context, widgetId))
            } else {
                views.setViewVisibility(R.id.ListView, View.VISIBLE)
                views.setViewVisibility(R.id.Empty, View.GONE)

                val serviceIntent = Intent(context, PinnedNoteWidgetService::class.java).apply {
                    putExtra(EXTRA_NOTE_ID, noteId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    // El sistema puede reusar por error el mismo
                    // RemoteViewsFactory entre widgets si dos Intents de
                    // servicio se consideran "iguales" (Intent.filterEquals
                    // no mira los extras) — un data Uri distinto por
                    // widget+nota evita ese cruce.
                    data = Uri.parse("bouncynotes://widget/$widgetId/$noteId")
                }
                views.setRemoteAdapter(R.id.ListView, serviceIntent)
                views.setEmptyView(R.id.ListView, R.id.Empty)

                // Plantilla de click para las filas de la lista, SIN action
                // fija: cada fila decide la suya propia (abrir nota vs.
                // reconfigurar) a través de su propio fill-in Intent — si la
                // plantilla ya trajera una action puesta, la de cada fila
                // quedaría ignorada (fillIn() no pisa campos ya definidos).
                val templateIntent = Intent(context, PinnedNoteWidgetProvider::class.java)
                val templatePendingIntent = PendingIntent.getBroadcast(
                    context, widgetId, templateIntent, pendingIntentFlags()
                )
                views.setPendingIntentTemplate(R.id.ListView, templatePendingIntent)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
            if (noteId != null) {
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.ListView)
            }
        }

        // La llama NoteRepository después de cualquier save/delete/purgeOldTrash.
        // Recorre TODAS las instancias activas del widget (vía AppWidgetManager,
        // la fuente de verdad del sistema — no una lista propia que podría
        // desincronizarse) y las refresca.
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PinnedNoteWidgetProvider::class.java))
            ids.forEach { widgetId -> updateWidget(context, manager, widgetId) }
        }

        fun reconfigurePendingIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, PinnedNoteWidgetProvider::class.java).apply {
                action = ACTION_RECONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("bouncynotes://widget/reconfigure/$widgetId")
            }
            return PendingIntent.getBroadcast(context, widgetId, intent, pendingIntentFlags())
        }

        // Fill-in Intent (no PendingIntent): lo usa el Factory para las
        // filas de la lista, que comparten la plantilla sin action de
        // updateWidget() de arriba.
        fun openNoteFillInIntent(noteId: Long): Intent =
            Intent().apply {
                action = ACTION_OPEN_NOTE
                putExtra(EXTRA_NOTE_ID, noteId)
                data = Uri.parse("bouncynotes://widget/open/$noteId")
            }

        fun reconfigureFillInIntent(widgetId: Int): Intent =
            Intent().apply {
                action = ACTION_RECONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse("bouncynotes://widget/reconfigure/$widgetId")
            }

        fun toggleChecklistItemFillInIntent(noteId: Long, itemIndex: Int): Intent =
            Intent().apply {
                action = ACTION_TOGGLE_CHECKLIST_ITEM
                putExtra(EXTRA_NOTE_ID, noteId)
                putExtra(EXTRA_ITEM_INDEX, itemIndex)
                data = Uri.parse("bouncynotes://widget/toggle/$noteId/$itemIndex")
            }

        // FLAG_MUTABLE es obligatorio para un PendingIntentTemplate: el
        // sistema necesita poder completarlo con el fill-in Intent de cada
        // fila al tocarla. Es una constante estable del SDK (un simple int),
        // segura de usar sin importar la versión de Android del dispositivo
        // — en versiones viejas ese bit no significa nada y se ignora, no
        // rompe nada.
        fun pendingIntentFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    }
}
