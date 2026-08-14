package com.example.notes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class NoteType { TEXT, CHECKLIST }

data class ChecklistItem(
    val text: String = "",
    val checked: Boolean = false
)

data class NoteImage(
    val path: String,
    val caption: String = ""
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: NoteType = NoteType.TEXT,
    val title: String = "",
    val content: String = "",
    val checklistItems: List<ChecklistItem> = emptyList(),
    val images: List<NoteImage> = emptyList(),
    val labels: List<String> = emptyList(),
    val color: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val isPrivate: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
