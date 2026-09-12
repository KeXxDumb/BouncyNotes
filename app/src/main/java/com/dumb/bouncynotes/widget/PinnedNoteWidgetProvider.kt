package com.dumb.bouncynotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.dumb.bouncynotes.MainActivity
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.NoteRepository
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.SettingsCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val ACTION_OPEN_NOTE = "com.dumb.bouncynotes.widget.ACTION_OPEN_NOTE"
private const val ACTION_TOGGLE_CHECKLIST_ITEM = "com.dumb.bouncynotes.widget.ACTION_TOGGLE_CHECKLIST_ITEM"
const val EXTRA_NOTE_ID = "com.dumb.bouncynotes.widget.EXTRA_NOTE_ID"
// Filtrar logcat con: adb logcat -s BouncyNotesWidget
// (o "grep BouncyNotesWidget" sobre un logcat ya volcado a archivo).
private const val TAG_WIDGET = "BouncyNotesWidget"
private const val EXTRA_ITEM_INDEX = "com.dumb.bouncynotes.widget.EXTRA_ITEM_INDEX"

class PinnedNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> updateWidget(context, appWidgetManager, widgetId) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            PinnedNoteWidgetPrefs.removeWidget(context, widgetId)
            WidgetAppearancePrefs.removeWidget(context, widgetId)
        }
    }

    // Los clicks dentro de la lista del widget (ListView + RemoteViewsService)
    // viajan como BROADCASTS hacia este mismo provider — setPendingIntentTemplate
    // exige específicamente un PendingIntent.getBroadcast(), no uno de Activity.
    // Por eso "abrir la nota" y "reconfigurar" se resuelven acá, no directo
    // desde el Factory.
    //
    // BUG (reportado): tocar una fila para abrir la nota no hacía nada —
    // recién se abría ahí la próxima vez que se abría la app a mano (con
    // la app ya corriendo en segundo plano). Ver el comentario largo en
    // MainActivity (arriba de la clase) con la explicación completa: la
    // causa real era FLAG_ACTIVITY_CLEAR_TOP, que con launchMode "standard"
    // no alcanza a forzar la recreación de la Activity porque el
    // comportamiento de "traer la tarea al frente tal cual estaba" de
    // NEW_TASK tiene prioridad. Se cambió a FLAG_ACTIVITY_CLEAR_TASK,
    // confirmado comparando con NotallyX (la app de referencia de este
    // proyecto), que usa esa combinación para exactamente esto.
    //
    // De paso, en vez de context.startActivity(...) directo se manda un
    // PendingIntent.getActivity(...) con .send() — no era la causa
    // principal del bug, pero es el patrón más confiable para pasar el
    // permiso de "abrir una Activity" cuando de por medio hay un salto por
    // un BroadcastReceiver (como acá), así que se dejó como refuerzo.
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG_WIDGET, "onReceive() action=${intent.action} noteId=${intent.getLongExtra(EXTRA_NOTE_ID, -1L)}")
        when (intent.action) {
            ACTION_OPEN_NOTE -> {
                val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
                Log.d(TAG_WIDGET, "ACTION_OPEN_NOTE noteId=$noteId")
                if (noteId == 0L) {
                    Log.w(TAG_WIDGET, "noteId llegó en 0 — MainActivity va a IGNORAR esto (0L = \"nada que abrir\")")
                }
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("openNoteId", noteId)
                    data = Uri.parse("bouncynotes://widget/mainactivity/open/$noteId")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                sendActivityPendingIntent(context, noteId.toInt(), openIntent)
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

    // Envía un PendingIntent.getActivity(...) en vez de llamar a
    // context.startActivity(...) a mano — ver el comentario largo arriba de
    // onReceive() con la explicación completa del bug que esto arregla.
    // requestCode único por Intent (no una constante fija) para que dos
    // notas/widgets distintos no compartan ni pisen el mismo PendingIntent
    // cacheado.
    private fun sendActivityPendingIntent(context: Context, requestCode: Int, activityIntent: Intent) {
        val pendingIntent = PendingIntent.getActivity(
            context, requestCode, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            pendingIntent.send()
            Log.d(TAG_WIDGET, "sendActivityPendingIntent() .send() OK, requestCode=$requestCode data=${activityIntent.data}")
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG_WIDGET, "PendingIntent.send() tiró CanceledException, cae a startActivity() directo", e)
            // No debería pasar nunca (lo acabamos de crear nosotros mismos),
            // pero si el sistema lo cancela por lo que sea, mejor intentar
            // el camino directo que quedarse sin abrir nada.
            context.startActivity(activityIntent)
        }
    }

    private suspend fun toggleChecklistItem(context: Context, noteId: Long, itemIndex: Int) {
        val dao = NoteDatabase.getInstance(context).noteDao()
        val note = dao.getById(noteId) ?: return
        if (itemIndex !in note.checklistItems.indices) return
        val updatedItems = note.checklistItems.toMutableList().apply {
            this[itemIndex] = this[itemIndex].copy(checked = !this[itemIndex].checked)
        }
        // Mismo criterio que ya aplica NoteEditScreen al tildar un ítem
        // desde el editor (sortedBy { it.checked }, ESTABLE: los no
        // marcados conservan su orden entre sí) — antes esto NO pasaba acá,
        // así que tildar desde el widget dejaba el ítem en el medio de la
        // lista en vez de mandarlo al final aunque el usuario tuviera
        // activado "marcados al final" en Ajustes.
        val finalItems = if (SettingsCache.read(context).autoSortChecked) {
            updatedItems.sortedBy { it.checked }
        } else {
            updatedItems
        }
        // Pasa por NoteRepository (no dao.upsert directo) para que dispare
        // el mismo refresh que cualquier otro guardado: así se actualiza
        // ESTE widget y también el de "última nota editada", si esta nota
        // fuera además la más reciente.
        NoteRepository(dao, context).save(
            note.copy(checklistItems = finalItems, updatedAt = System.currentTimeMillis())
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
            val colors = resolveWidgetColors(context, widgetId)
            applyWidgetBackground(views, R.id.Layout, colors)
            views.setTextColor(R.id.Empty, colors.textSecondary)
            views.setTextColor(R.id.Title, colors.textPrimary)
            views.setInt(R.id.HeaderDivider, "setBackgroundColor", colors.divider)

            if (noteId == null) {
                views.setViewVisibility(R.id.HeaderRow, View.GONE)
                views.setViewVisibility(R.id.ListView, View.GONE)
                views.setViewVisibility(R.id.Empty, View.VISIBLE)
                views.setOnClickPendingIntent(
                    R.id.Empty,
                    configureActivityPendingIntent(context, widgetId, PinnedNoteWidgetConfigActivity::class.java)
                )
            } else {
                views.setViewVisibility(R.id.HeaderRow, View.VISIBLE)
                views.setViewVisibility(R.id.ListView, View.VISIBLE)
                views.setViewVisibility(R.id.Empty, View.GONE)

                // Antes esto lo resolvía el Factory, como la fila 0 del
                // ListView. Ahora que el título es una vista fija (ver
                // widget_pinned_note.xml), hace falta acá — una consulta
                // por id de Room es prácticamente instantánea, así que un
                // runBlocking puntual no es un problema real (mismo
                // criterio que ya usa el propio Factory para traer la nota
                // entera).
                val note = runBlocking {
                    NoteDatabase.getInstance(context).noteDao().getById(noteId)
                }
                views.setTextViewText(R.id.Title, note?.title?.ifBlank { "(Sin título)" } ?: "")
                // Click DIRECTO, no fill-in — ver el comentario largo en
                // widget_pinned_note.xml sobre por qué esto es justo lo que
                // arregla el bug de Xiaomi/MIUI.
                views.setOnClickPendingIntent(R.id.Title, openNotePendingIntent(context, noteId))
                // Click DIRECTO también para reconfigurar (antes iba por
                // ACTION_RECONFIGURE, un broadcast) — mismo criterio que
                // openNotePendingIntent, para no depender de ningún
                // receiver de por medio.
                views.setOnClickPendingIntent(
                    R.id.ChangeNote,
                    configureActivityPendingIntent(context, widgetId, PinnedNoteWidgetConfigActivity::class.java)
                )

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

                // BUG (reportado, Xiaomi/MIUI, confirmado con logcat):
                // tocar una fila de CONTENIDO (texto/imagen) para abrir la
                // nota no hacía nada — el log mostraba
                // "sendActivityPendingIntent() .send() OK" (sin excepción)
                // pero jamás un MainActivity.onCreate()/onNewIntent()
                // después: el sistema acepta el envío del PendingIntent
                // pero bloquea en silencio que la Activity se muestre.
                // Encaja con las restricciones de "Background Activity
                // Launch" — más estrictas para un PendingIntent de
                // BROADCAST que dispara un startActivity() DENTRO de
                // onReceive() (justo lo que hacía ACTION_OPEN_NOTE) que
                // para un PendingIntent de Activity DIRECTO.
                //
                // El arreglo: ya que este widget siempre muestra UNA sola
                // nota fija, TODAS las filas de una nota de tipo TEXTO
                // quieren hacer lo MISMO al tocarse (abrir esta nota
                // puntual) — no hace falta el mecanismo de
                // "plantilla + fill-in Intent" en absoluto para eso: la
                // plantilla puede ser directamente el mismo
                // PendingIntent.getActivity() ya probado y confiable que
                // usa el título (openNotePendingIntent). Las notas de tipo
                // CHECKLIST siguen necesitando la plantilla de broadcast
                // (ACTION_TOGGLE_CHECKLIST_ITEM), porque ahí cada fila SÍ
                // necesita distinguirse por índice de ítem — pero esas
                // filas ya no intentan abrir la nota al tocar el texto
                // (arreglado antes: tocar el texto tilda, igual que el
                // casillero), así que no les pega este problema.
                val templatePendingIntent = if (note?.type == NoteType.CHECKLIST) {
                    val templateIntent = Intent(context, PinnedNoteWidgetProvider::class.java)
                    PendingIntent.getBroadcast(context, widgetId, templateIntent, pendingIntentFlags())
                } else {
                    openNotePendingIntent(context, noteId)
                }
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

        // Click directo para el título (vista fija, fuera del ListView) —
        // no pasa por onReceive() de este provider en absoluto, así que no
        // depende del mecanismo de listas para nada. CLEAR_TASK, mismo
        // motivo que en sendActivityPendingIntent/MainActivity.
        fun openNotePendingIntent(context: Context, noteId: Long): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra("openNoteId", noteId)
                data = Uri.parse("bouncynotes://widget/mainactivity/open/$noteId")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            return PendingIntent.getActivity(
                context, noteId.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Fill-in Intent (no PendingIntent): lo usa el Factory para las
        // filas de CONTENIDO de la lista (texto/imagen/ítem de checklist),
        // que comparten la plantilla sin action de updateWidget() de
        // arriba. El título YA NO usa esto — tiene su propio click directo
        // (ver openNotePendingIntent).
        fun openNoteFillInIntent(noteId: Long): Intent {
            Log.d(TAG_WIDGET, "openNoteFillInIntent() construido con noteId=$noteId")
            return Intent().apply {
                action = ACTION_OPEN_NOTE
                putExtra(EXTRA_NOTE_ID, noteId)
                data = Uri.parse("bouncynotes://widget/open/$noteId")
            }
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
