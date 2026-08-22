package com.dumb.bouncynotes.data

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.dumb.bouncynotes.MainActivity

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
//
// RAÍZ del bug "los recordatorios no funcionan": la alarma principal se
// programaba con setExactAndAllowWhileIdle(). Esa API SÍ dispara con Doze
// activo, pero para el sistema es una alarma "de fondo" cualquiera, así que
// en la mayoría de los fabricantes (Samsung, Xiaomi/MIUI, etc., y también
// stock Android con "optimización de batería" activada) el gestor de
// batería puede demorarla, agruparla con otras, o directamente matarla si
// la app no está exenta de esas restricciones — sin ningún error visible,
// simplemente nunca suena. Se comparó contra una app de reloj/alarmas real
// (ClockYou, github.com/you-apps/ClockYou) para confirmarlo: ese tipo de
// apps programan la alarma principal con AlarmManager.setAlarmClock() en
// vez de setExactAndAllowWhileIdle(). setAlarmClock() es la única API que
// Android trata como una alarma de reloj real (aparece el ícono de alarma
// en la barra de estado) y por eso queda EXENTA de Doze/App Standby y,
// en la práctica, de casi todos los "battery savers" de fabricantes — es
// lo mismo que usa la app nativa de Reloj. Por eso acá la alarma principal
// pasa a usar setAlarmClock(); el aviso de 1h antes (secundario, no crítico)
// se deja con setExactAndAllowWhileIdle() como antes, igual que ClockYou
// separa su "pre-alarm".
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

    // PendingIntent que se dispara si el usuario toca el ícono de alarma que
    // el sistema muestra en la barra de estado (solo relevante para
    // setAlarmClock, que lo exige como segundo parámetro): abre la nota
    // directamente, igual que la notificación.
    private fun showIntent(context: Context, noteId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openNoteId", noteId)
        }
        return PendingIntent.getActivity(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleOne(context: Context, noteId: Long, triggerAt: Long, isAdvance: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, noteId, isAdvance)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // Sin permiso de alarmas exactas: mejor una alarma aproximada
                // que ninguna. El usuario puede habilitar el permiso desde
                // Ajustes para que suene puntual (ver requestExactAlarmPermission).
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else if (!isAdvance) {
                // La alarma principal: setAlarmClock() en vez de
                // setExactAndAllowWhileIdle(), para quedar exenta de
                // Doze/ahorro de batería (ver comentario arriba del objeto).
                val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent(context, noteId))
                alarmManager.setAlarmClock(info, pi)
            } else {
                // El aviso de 1h antes es secundario: no hace falta que
                // aparezca como "próxima alarma" del sistema.
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    // --- Permisos que, sin bloquear el guardado del recordatorio, afectan a
    // que realmente llegue a sonar en la práctica ------------------------

    // true si Android ya no aplica restricciones de ahorro de batería a esta
    // app. Sin esto, muchos fabricantes (Samsung, Xiaomi/MIUI, etc.) pueden
    // matar el proceso o demorar la alarma igual, aunque se haya usado
    // setAlarmClock().
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    // Abre el diálogo del sistema para pedir la exención de optimización de
    // batería. Requiere Activity (no Application) porque es un startActivity
    // normal, no algo que tenga sentido lanzar sin una pantalla visible.
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        activity.startActivity(intent)
    }

    // Abre la pantalla de Ajustes del sistema donde el usuario puede
    // habilitar "Alarmas y recordatorios" para esta app (Android 12+). Sin
    // esto, canScheduleExact() puede quedar en false indefinidamente sin que
    // el usuario tenga forma de arreglarlo desde dentro de la app.
    fun requestExactAlarmPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        activity.startActivity(intent)
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
