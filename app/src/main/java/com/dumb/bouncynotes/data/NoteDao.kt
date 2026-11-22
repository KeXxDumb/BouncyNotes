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

    // Notas con fecha límite pendiente. Se usa al arrancar la app y al
    // reiniciar el teléfono para volver a programar sus alarmas de caducidad
    // (AlarmManager no sobrevive a ninguna de las dos cosas).
    @Query("SELECT * FROM notes WHERE expiresAt IS NOT NULL AND deletedAt IS NULL")
    suspend fun getAllWithExpiry(): List<Note>

    // Igual que arriba pero INCLUYE la papelera: una nota caducada debe
    // borrarse para siempre aunque el usuario la hubiera movido ahí.
    @Query("SELECT * FROM notes WHERE expiresAt IS NOT NULL")
    suspend fun getAllWithExpiryIncludingTrashed(): List<Note>

    // Borra TODAS las notas caducadas, estén o no en la papelera: la fecha
    // límite es definitiva, no respeta "papelera".
    @Query("DELETE FROM notes WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
    suspend fun deleteExpired(now: Long)

    // Usado al reiniciar el teléfono para volver a programar todas las alarmas
    // de recordatorio pendientes (AlarmManager no sobrevive un reinicio).
    @Query("SELECT * FROM notes WHERE reminderAt IS NOT NULL AND deletedAt IS NULL")
    suspend fun getAllWithReminders(): List<Note>
}
