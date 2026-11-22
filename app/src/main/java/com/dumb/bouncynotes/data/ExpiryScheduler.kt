package com.dumb.bouncynotes.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Programa el borrado automático de las notas que vinieron en un .bnotes con
// fecha límite. Misma idea que ReminderScheduler (AlarmManager, no
// WorkManager) pero con request codes desplazados para no pisar las alarmas
// de recordatorio de la MISMA nota.
object ExpiryScheduler {

    private const val REQUEST_CODE_OFFSET = 2_000_000
    private const val ACTION_EXPIRE = "com.dumb.bouncynotes.EXPIRE"
    const val EXTRA_NOTE_ID = "expire_note_id"

    private fun pendingIntent(context: Context, noteId: Long): PendingIntent {
        val intent = Intent(context, ExpiryReceiver::class.java).apply {
            action = ACTION_EXPIRE
            data = Uri.parse("bouncynotes://expire/$noteId")
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_OFFSET + noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, note: Note) {
        val expiresAt = note.expiresAt ?: return
        cancel(context, note.id)

        // Si la fecha ya pasó (reloj cambiado, app abierta tarde, etc.), se
        // purga ya mismo en vez de programar una alarma en el pasado.
        if (expiresAt <= System.currentTimeMillis()) {
            CoroutineScope(Dispatchers.IO).launch {
                ExpiryManager.purgeNote(context, note.id)
            }
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, note.id)
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expiresAt, pi)
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, expiresAt, pi)
        }
    }

    fun cancel(context: Context, noteId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, noteId))
    }

    suspend fun rescheduleAll(context: Context) {
        val dao = NoteDatabase.getInstance(context).noteDao()
        dao.getAllWithExpiry().forEach { note ->
            schedule(context, note)
        }
    }
}
