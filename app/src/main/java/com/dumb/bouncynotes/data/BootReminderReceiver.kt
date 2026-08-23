package com.dumb.bouncynotes.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// AlarmManager se olvida de TODAS las alarmas programadas no solo al
// reiniciar el teléfono, sino también cada vez que la app se actualiza
// (se instala una versión nueva encima de la vieja) — Android manda
// ACTION_MY_PACKAGE_REPLACED en ese caso, igual que manda BOOT_COMPLETED al
// reiniciar. Antes esta clase solo escuchaba BOOT_COMPLETED: en un flujo de
// desarrollo donde se instala una build nueva por cada prueba (en vez de
// reiniciar el teléfono de verdad), cada reinstalación borraba en silencio
// cualquier recordatorio ya programado en una sesión anterior, y nunca se
// volvía a armar solo hasta el próximo reinicio real — algo que puede
// tardar semanas en un uso normal. Esto también importa para el caso real
// (no solo de prueba): un usuario final que actualiza la app desde una
// futura release también perdería sus recordatorios programados sin esto.
class BootReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
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
