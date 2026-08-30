package com.dumb.bouncynotes.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dumb.bouncynotes.MainActivity
import com.dumb.bouncynotes.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_IS_ADVANCE = "is_advance"
        const val EXTRA_IS_TEST = "is_test"

        // Dos canales separados en vez de uno solo: en Android 8+ (Oreo) la
        // prioridad/heads-up de una notificación la decide el CANAL, no el
        // setPriority() del Builder (ese solo se respeta en versiones
        // viejas). El aviso de "falta 1 hora" no debe interrumpir como
        // notificación flotante (el usuario tiene tiempo de sobra), pero el
        // de "ya es la hora" sí debe aparecer como heads-up.
        const val CHANNEL_ID_EXACT = "note_reminders_exact"
        const val CHANNEL_ID_ADVANCE = "note_reminders_advance"

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID_EXACT) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID_EXACT,
                        "Recordatorio (a la hora exacta)",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Aviso flotante justo a la hora del recordatorio de una nota"
                    }
                )
            }
            if (manager.getNotificationChannel(CHANNEL_ID_ADVANCE) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID_ADVANCE,
                        "Aviso previo (1 hora antes)",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Aviso 1 hora antes del recordatorio de una nota, sin interrumpir como notificación flotante"
                    }
                )
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
        val isAdvance = intent.getBooleanExtra(EXTRA_IS_ADVANCE, false)
        val isTest = intent.getBooleanExtra(EXTRA_IS_TEST, false)

        if (isTest) {
            // La alarma de prueba no tiene una nota real detrás: se muestra
            // directo, sin ir a la base de datos.
            ensureChannels(context)
            showNotificationRaw(
                context = context,
                channelId = CHANNEL_ID_EXACT,
                notificationId = noteId.toInt(),
                title = "Recordatorio de prueba",
                body = "Si ves esto, la alarma y la notificación funcionan bien en este teléfono.",
                contentIntentNoteId = null
            )
            return
        }

        if (noteId == 0L) return

        // Ir a buscar la nota (para título/contenido, y para poder apagar o
        // reprogramar el recordatorio) es async; usamos goAsync() para que
        // el sistema no mate el receiver antes de que termine.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = NoteDatabase.getInstance(context).noteDao()
                val note = dao.getById(noteId)
                if (note != null && note.deletedAt == null) {
                    showNotification(context, note, isAdvance)

                    // Solo la alarma PRINCIPAL (isAdvance=false, "ya es la
                    // hora") decide el destino del recordatorio; el aviso de
                    // 1h antes no apaga ni reprograma nada, es puramente
                    // informativo.
                    if (!isAdvance) {
                        when {
                            note.reminderDays.isNotEmpty() -> {
                                // Días de la semana: "ahora" ya pasó la
                                // ocurrencia de hoy, así que programar de
                                // nuevo con la misma nota calcula
                                // naturalmente la próxima (semana que viene,
                                // o el próximo día elegido).
                                ReminderScheduler.schedule(context, note)
                            }
                            note.reminderCalendarDates.isNotEmpty() -> {
                                val anchor = intent.getLongExtra(ReminderScheduler.EXTRA_CALENDAR_ANCHOR, -1L)
                                val updatedDates = if (anchor == -1L || anchor !in note.reminderCalendarDates) {
                                    // No debería pasar, pero por las dudas: si
                                    // no se sabe qué fecha sonó, no se toca el
                                    // set (mejor dejarlo como estaba que
                                    // borrar la fecha equivocada).
                                    note.reminderCalendarDates
                                } else if (note.reminderCalendarRecurring) {
                                    // Recurrente: se reemplaza por la misma
                                    // fecha un año después, nunca se descarta.
                                    (note.reminderCalendarDates - anchor) + ReminderScheduler.advanceCalendarAnchorByOneYear(anchor)
                                } else {
                                    // Una sola vez: se descarta esa fecha.
                                    note.reminderCalendarDates - anchor
                                }
                                val updatedNote = if (updatedDates.isEmpty()) {
                                    // Ya sonaron todas: se apaga el recordatorio.
                                    note.copy(reminderCalendarDates = emptySet(), reminderAt = null)
                                } else {
                                    note.copy(reminderCalendarDates = updatedDates)
                                }
                                dao.upsert(updatedNote)
                                if (updatedDates.isNotEmpty()) {
                                    ReminderScheduler.schedule(context, updatedNote)
                                }
                            }
                            else -> {
                                // Modo simple de una sola vez: se apaga solo al sonar.
                                dao.upsert(note.copy(reminderAt = null))
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotificationRaw(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        contentIntentNoteId: Long?
    ) {
        val builder = NotificationCompat.Builder(context, channelId)
            // Silueta blanca sobre transparente, formato que pide Android
            // para íconos de notificación — recurso independiente del
            // sistema de íconos de la app (que ahora es plano, sin capas).
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(
                if (channelId == CHANNEL_ID_EXACT) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )

        if (contentIntentNoteId != null) {
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("openNoteId", contentIntentNoteId)
            }
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    contentIntentNoteId.toInt(),
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    private fun showNotification(context: Context, note: Note, isAdvance: Boolean) {
        ensureChannels(context)

        val title = if (isAdvance) {
            "En 1 hora: ${note.title.ifBlank { "recordatorio" }}"
        } else {
            note.title.ifBlank { "Recordatorio" }
        }
        val body = when {
            note.type == NoteType.CHECKLIST -> note.checklistItems.take(3).joinToString(", ") { it.text }
            else -> stripFormattingMarkers(note.content).take(120)
        }.ifBlank { "Toca para abrir la nota" }

        // IDs distintos para el aviso de 1 hora antes y el recordatorio
        // final: así, si por lo que sea llegan a coincidir en el tiempo, no
        // se pisan una a la otra en la bandeja de notificaciones.
        val notificationId = if (isAdvance) note.id.toInt() + 1_000_000 else note.id.toInt()

        showNotificationRaw(
            context = context,
            channelId = if (isAdvance) CHANNEL_ID_ADVANCE else CHANNEL_ID_EXACT,
            notificationId = notificationId,
            title = title,
            body = body,
            contentIntentNoteId = note.id
        )
    }
}
