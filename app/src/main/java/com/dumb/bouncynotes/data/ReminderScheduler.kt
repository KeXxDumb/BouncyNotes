package com.dumb.bouncynotes.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

// Centraliza la programación/cancelación de las alarmas de recordatorio de
// las notas. Usa AlarmManager directamente (no WorkManager) porque
// necesitamos que suene a una hora exacta elegida por el usuario, incluso
// con la app cerrada o el teléfono en reposo (Doze).
//
// Por cada nota con recordatorio se programan hasta DOS alarmas:
//  - una "de aviso", 1 hora antes, para avisar con anticipación.
//  - la principal, exactamente a la hora elegida.
// Cada una tiene su propio PendingIntent (con un request code distinto), así
// que son independientes: cancelar/reprogramar una no toca la otra.
object ReminderScheduler {

    private const val ADVANCE_MILLIS = 60L * 60L * 1000L // 1 hora
    private const val ADVANCE_REQUEST_CODE_OFFSET = 1_000_000

    private fun pendingIntent(context: Context, noteId: Long, isAdvance: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = if (isAdvance) "com.dumb.bouncynotes.REMINDER_ADVANCE" else "com.dumb.bouncynotes.REMINDER"
            data = android.net.Uri.parse("bouncynotes://reminder/$noteId${if (isAdvance) "/advance" else ""}")
            putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderReceiver.EXTRA_IS_ADVANCE, isAdvance)
        }
        val requestCode = if (isAdvance) ADVANCE_REQUEST_CODE_OFFSET + noteId.toInt() else noteId.toInt()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    // true si el sistema realmente puede disparar una alarma exacta ahora
    // mismo (en Android 12+ el usuario puede haber revocado el permiso desde
    // Ajustes del sistema).
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun schedule(context: Context, note: Note) {
        val triggerAt = note.reminderAt ?: return
        cancel(context, note.id)

        if (triggerAt > System.currentTimeMillis()) {
            scheduleOne(context, note.id, triggerAt, isAdvance = false)
        }
        val advanceAt = triggerAt - ADVANCE_MILLIS
        // Si el recordatorio se programó con menos de 1 hora de anticipación,
        // no tiene sentido un aviso "1 hora antes" que ya quedó en el pasado.
        if (advanceAt > System.currentTimeMillis()) {
            scheduleOne(context, note.id, advanceAt, isAdvance = true)
        }
    }

    private fun scheduleOne(context: Context, noteId: Long, triggerAt: Long, isAdvance: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, noteId, isAdvance)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // Sin permiso de alarmas exactas: mejor una alarma aproximada
                // que ninguna. El usuario puede habilitar el permiso desde
                // Ajustes para que suene puntual.
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context, noteId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, noteId, isAdvance = false))
        alarmManager.cancel(pendingIntent(context, noteId, isAdvance = true))
    }

    // Se llama al reiniciar el teléfono: AlarmManager no persiste las alarmas
    // programadas entre reinicios, así que hay que volver a darlas de alta.
    suspend fun rescheduleAll(context: Context) {
        val dao = NoteDatabase.getInstance(context).noteDao()
        val repository = NoteRepository(dao)
        repository.getAllWithReminders().forEach { note ->
            if (note.reminderAt != null && note.reminderAt > System.currentTimeMillis()) {
                schedule(context, note)
            }
        }
    }
}
