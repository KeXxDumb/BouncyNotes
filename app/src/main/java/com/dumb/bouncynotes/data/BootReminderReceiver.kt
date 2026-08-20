package com.dumb.bouncynotes.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// AlarmManager olvida todas las alarmas programadas cuando el teléfono se
// reinicia, así que hay que volver a programar los recordatorios pendientes.
class BootReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.rescheduleAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
