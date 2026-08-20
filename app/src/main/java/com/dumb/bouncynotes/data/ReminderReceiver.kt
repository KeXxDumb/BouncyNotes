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
        const val CHANNEL_ID = "note_reminders"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Recordatorios de notas",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Avisos para las notas con recordatorio programado"
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
        val isAdvance = intent.getBooleanExtra(EXTRA_IS_ADVANCE, false)
        if (noteId == 0L) return

        // Ir a buscar la nota (para título/contenido) es async; usamos goAsync()
        // para que el sistema no mate el receiver antes de que termine.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = NoteDatabase.getInstance(context).noteDao()
                val note = dao.getById(noteId)
                if (note != null && note.deletedAt == null) {
                    showNotification(context, note, isAdvance)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, note: Note, isAdvance: Boolean) {
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("openNoteId", note.id)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            note.id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isAdvance) {
            "En 1 hora: ${note.title.ifBlank { "recordatorio" }}"
        } else {
            note.title.ifBlank { "Recordatorio" }
        }
        val body = when {
            note.type == NoteType.CHECKLIST -> note.checklistItems.take(3).joinToString(", ") { it.text }
            else -> stripFormattingMarkers(note.content).take(120)
        }.ifBlank { "Toca para abrir la nota" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .build()

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            // IDs distintos para el aviso de 1 hora antes y el recordatorio
            // final: así, si por lo que sea llegan a coincidir en el tiempo,
            // no se pisan una a la otra en la bandeja de notificaciones.
            val notificationId = if (isAdvance) note.id.toInt() + 1_000_000 else note.id.toInt()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
