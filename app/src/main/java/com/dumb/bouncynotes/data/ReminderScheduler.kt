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
import java.util.Calendar

// Centraliza la programación/cancelación de las alarmas de recordatorio de
// las notas. Usa AlarmManager directamente (no WorkManager) porque
// necesitamos que suene a una hora exacta elegida por el usuario, incluso
// con la app cerrada o el teléfono en reposo (Doze).
//
// Por cada nota con recordatorio se programan hasta DOS alarmas:
//  - una "de aviso", 1 hora antes (notificación normal, sin heads-up).
//  - la principal, exactamente a la hora elegida (heads-up/flotante).
// Cada una tiene su propio PendingIntent (con un request code distinto), así
// que son independientes: cancelar/reprogramar una no toca la otra.
//
// DOS MODOS, según Note.reminderDays:
//  - VACÍO: recordatorio de una sola vez, a Note.reminderAt tal cual. Al
//    sonar, ReminderReceiver lo apaga solo (pone reminderAt = null en la BD).
//  - NO VACÍO: recordatorio recurrente. Note.reminderAt se usa solo como
//    "ancla" para sacarle la hora/minuto (el día de esa fecha no importa
//    para nada); acá se calcula la PRÓXIMA fecha real en la que cae uno de
//    esos días de la semana. Al sonar, ReminderReceiver vuelve a llamar a
//    schedule() con la misma nota: como "ahora" ya avanzó más allá de la
//    ocurrencia de hoy, el cálculo naturalmente da la ocurrencia de la
//    semana siguiente (o el próximo día elegido más cercano). Sigue así
//    indefinidamente hasta que el usuario lo apague a mano.
//
// RAÍZ del bug "los recordatorios no funcionan" (encontrada en una sesión
// anterior): la alarma principal se programaba con
// setExactAndAllowWhileIdle(). Esa API SÍ dispara con Doze activo, pero
// para el sistema es una alarma "de fondo" cualquiera, así que en la
// mayoría de los fabricantes (Samsung, Xiaomi/MIUI, etc.) el gestor de
// batería puede demorarla, agruparla o matarla directamente — sin ningún
// error visible. Comparado contra ClockYou (github.com/you-apps/ClockYou,
// una app de alarmas real): usan AlarmManager.setAlarmClock() para la
// alarma principal, la única API que Android trata como alarma de reloj
// real (exenta de Doze/App Standby) — es lo que se usa acá también. El
// aviso de 1h antes (secundario) usa setExactAndAllowWhileIdle(), igual que
// el "pre-alarm" de ClockYou.
//
// Aparte de la API de AlarmManager usada, se encontraron y arreglaron dos
// bugs bien distintos que hacían que esto pareciera "no funcionar en
// absoluto" sin que fuera culpa de AlarmManager:
//  1. Confirmar el recordatorio en el editor solo actualizaba el estado en
//     memoria; el guardado real (que es lo que llama a este objeto) recién
//     pasaba al salir de la pantalla con la flecha de volver. Si el
//     usuario salía de cualquier otra forma (botón Home, etc.), el
//     recordatorio nunca se llegaba a programar. Arreglado en
//     NoteEditScreen: ahora se guarda al toque, apenas se confirma.
//  2. BootReminderReceiver solo escuchaba BOOT_COMPLETED. Instalar una
//     build nueva de la app (algo muy común en un flujo de desarrollo con
//     reinstalaciones frecuentes) también le borra las alarmas a
//     AlarmManager, y sin escuchar también MY_PACKAGE_REPLACED esas alarmas
//     quedaban perdidas hasta el próximo reinicio real del teléfono.
object ReminderScheduler {

    private const val ADVANCE_MILLIS = 60L * 60L * 1000L // 1 hora
    private const val ADVANCE_REQUEST_CODE_OFFSET = 1_000_000

    private fun pendingIntent(context: Context, noteId: Long, isAdvance: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = if (isAdvance) "com.dumb.bouncynotes.REMINDER_ADVANCE" else "com.dumb.bouncynotes.REMINDER"
            data = Uri.parse("bouncynotes://reminder/$noteId${if (isAdvance) "/advance" else ""}")
            putExtra(ReminderReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderReceiver.EXTRA_IS_ADVANCE, isAdvance)
        }
        val requestCode = if (isAdvance) ADVANCE_REQUEST_CODE_OFFSET + noteId.toInt() else noteId.toInt()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    // true si el sistema realmente puede disparar una alarma exacta ahora
    // mismo (en Android 12+ el usuario puede haber revocado el permiso desde
    // Ajustes del sistema). USE_EXACT_ALARM (declarado en el manifest) hace
    // que esto sea true de entrada sin que el usuario tenga que hacer nada,
    // pero se deja el chequeo real por si algo cambia en el futuro.
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    // Calcula el próximo epoch millis, estrictamente en el futuro, que caiga
    // en uno de `days` (valores de Calendar.DAY_OF_WEEK: SUNDAY=1..SATURDAY=7)
    // a la hora/minuto de `anchorMillis`. Revisa los próximos 7 días
    // (hoy incluido) y devuelve el primero que matchee y todavía no haya
    // pasado; si hoy es un día elegido pero la hora ya pasó, se salta solo
    // ese día (el bucle sigue y agarra la próxima ocurrencia real).
    private fun nextOccurrence(anchorMillis: Long, days: Set<Int>): Long? {
        if (days.isEmpty()) return null
        val anchor = Calendar.getInstance().apply { timeInMillis = anchorMillis }
        val hour = anchor.get(Calendar.HOUR_OF_DAY)
        val minute = anchor.get(Calendar.MINUTE)
        val now = System.currentTimeMillis()

        for (offset in 0..7) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.get(Calendar.DAY_OF_WEEK) in days && candidate.timeInMillis > now) {
                return candidate.timeInMillis
            }
        }
        return null // No debería pasar nunca: 7 días cubren toda la semana.
    }

    fun schedule(context: Context, note: Note) {
        val anchor = note.reminderAt ?: return
        cancel(context, note.id)

        val mainTrigger = if (note.reminderDays.isEmpty()) {
            anchor
        } else {
            nextOccurrence(anchor, note.reminderDays) ?: return
        }

        if (mainTrigger > System.currentTimeMillis()) {
            scheduleOne(context, note.id, mainTrigger, isAdvance = false)
        }
        val advanceAt = mainTrigger - ADVANCE_MILLIS
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
            if (!isAdvance) {
                val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent(context, noteId))
                alarmManager.setAlarmClock(info, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    // --- Permisos que, sin bloquear el guardado del recordatorio, afectan a
    // que realmente llegue a sonar en la práctica ------------------------

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        activity.startActivity(intent)
    }

    fun requestExactAlarmPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        activity.startActivity(intent)
    }

    private const val TEST_NOTE_ID = -1L

    fun scheduleTest(context: Context, secondsFromNow: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.dumb.bouncynotes.REMINDER_TEST"
            putExtra(ReminderReceiver.EXTRA_NOTE_ID, TEST_NOTE_ID)
            putExtra(ReminderReceiver.EXTRA_IS_ADVANCE, false)
            putExtra(ReminderReceiver.EXTRA_IS_TEST, true)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            TEST_NOTE_ID.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + secondsFromNow * 1000L
        val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent(context, TEST_NOTE_ID))
        alarmManager.setAlarmClock(info, pi)
    }

    fun cancel(context: Context, noteId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, noteId, isAdvance = false))
        alarmManager.cancel(pendingIntent(context, noteId, isAdvance = true))
    }

    // Se llama al reiniciar el teléfono O al actualizar/reinstalar la app
    // (ver BootReminderReceiver): AlarmManager no persiste las alarmas
    // programadas en ninguno de los dos casos, así que hay que volver a
    // darlas de alta. Para recordatorios recurrentes NO se filtra por
    // "reminderAt en el futuro" (ese valor es solo el ancla de hora/minuto,
    // puede estar perfectamente en el pasado) — se reprograman siempre,
    // dejando que schedule() calcule la próxima ocurrencia real.
    suspend fun rescheduleAll(context: Context) {
        val dao = NoteDatabase.getInstance(context).noteDao()
        val repository = NoteRepository(dao)
        repository.getAllWithReminders().forEach { note ->
            if (note.reminderAt == null) return@forEach
            if (note.reminderDays.isNotEmpty() || note.reminderAt > System.currentTimeMillis()) {
                schedule(context, note)
            }
        }
    }
}
