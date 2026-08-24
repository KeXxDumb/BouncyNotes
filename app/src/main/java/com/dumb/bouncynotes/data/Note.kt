package com.dumb.bouncynotes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NoteType { TEXT, CHECKLIST }

data class ChecklistItem(
    val text: String = "",
    val checked: Boolean = false
)

// Representa un ítem ya resuelto a una ruta de archivo real (usado por el
// visor a pantalla completa): puede ser una imagen (o gif) o un video.
data class NoteImage(
    val path: String,
    val caption: String = "",
    val isVideo: Boolean = false
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: NoteType = NoteType.TEXT,
    val title: String = "",
    // Para notas de TEXTO: texto plano con imágenes embebidas como [[img:archivo.jpg|descripción]]
    // en la posición exacta donde el usuario las insertó (igual que Markdown).
    val content: String = "",
    // Para notas de CHECKLIST.
    val checklistItems: List<ChecklistItem> = emptyList(),
    val labels: List<String> = emptyList(),
    val color: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val isPrivate: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Fecha/hora (epoch millis) en la que debe sonar el recordatorio de esta
    // nota, o null si no tiene ninguno programado.
    //
    // Hay tres modos, mutuamente excluyentes (ver ReminderScheduler para el
    // detalle de cómo se calcula cuándo suena cada uno):
    //  - reminderDays no vacío: modo "días de la semana". Acá reminderAt
    //    solo aporta la hora/minuto (el día se ignora); se repite cada
    //    semana en esos días, indefinidamente.
    //  - reminderCalendarDates no vacío: modo "calendario" (ver abajo).
    //    reminderAt no se usa para nada en este modo.
    //  - ninguno de los dos: modo simple de una sola vez, a reminderAt tal
    //    cual (compatibilidad con notas ya creadas antes de que existiera
    //    el modo calendario). Se apaga solo al sonar.
    val reminderAt: Long? = null,
    // Días de la semana en los que se repite el recordatorio, usando la
    // convención de java.util.Calendar (Calendar.SUNDAY=1 .. Calendar.SATURDAY=7).
    // Vacío = no está en modo "días de la semana" (ver reminderAt arriba).
    val reminderDays: Set<Int> = emptySet(),
    // Modo "calendario": un conjunto de fechas+hora (epoch millis), pensado
    // para casos como "cumpleaños de la familia" con una fecha por persona.
    // Todas comparten la misma hora/minuto (la de cada entrada, en la
    // práctica todas iguales porque se eligen con el mismo selector).
    //  - reminderCalendarRecurring = false ("una vez"): cada fecha sirve una
    //    sola vez; al sonar se DESCARTA de este set. Cuando el set queda
    //    vacío, el recordatorio completo se apaga solo (reminderAt pasa a
    //    null).
    //  - reminderCalendarRecurring = true ("cada año"): el AÑO de cada
    //    fecha se ignora para calcular cuándo suena (solo importan
    //    mes/día/hora/minuto); al sonar, esa fecha se reemplaza por la
    //    misma un año después, nunca se descarta.
    val reminderCalendarDates: Set<Long> = emptySet(),
    val reminderCalendarRecurring: Boolean = false
)
