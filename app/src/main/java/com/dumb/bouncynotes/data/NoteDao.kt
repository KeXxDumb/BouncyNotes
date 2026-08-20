package com.dumb.bouncynotes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC")
    fun getAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: Note): Long

    @Delete
    suspend fun delete(note: Note)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :threshold")
    suspend fun purgeOldTrash(threshold: Long)

    // Usado al reiniciar el teléfono para volver a programar todas las alarmas
    // de recordatorio pendientes (AlarmManager no sobrevive un reinicio).
    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND deletedAt IS NULL")
    suspend fun getAllWithReminders(): List<Note>
}
