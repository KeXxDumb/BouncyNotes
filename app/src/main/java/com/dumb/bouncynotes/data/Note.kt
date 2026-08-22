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
    val reminderAt: Long? = null
)
