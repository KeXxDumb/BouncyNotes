package com.dumb.bouncynotes.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {

    val notes: Flow<List<Note>> = dao.getAll()

    suspend fun getById(id: Long): Note? = dao.getById(id)

    suspend fun save(note: Note): Long = dao.upsert(note)

    suspend fun delete(note: Note) = dao.delete(note)

    suspend fun purgeOldTrash(threshold: Long) = dao.purgeOldTrash(threshold)

    suspend fun getAllWithReminders(): List<Note> = dao.getAllWithReminders()
}
