package com.dumb.bouncynotes.data

import android.content.Context

// Lógica real de borrado de notas caducadas. La llaman tres sitios distintos:
//  - ExpiryReceiver cuando salta la alarma exacta.
//  - BouncyNotesApp al arrancar (por si el teléfono estaba apagado a la hora).
//  - BootReminderReceiver tras reiniciar o actualizar la app.
// Así hay una sola implementación de "caducó -> borrar nota y sus medios".
object ExpiryManager {

    suspend fun purgeAllExpired(context: Context) {
        val dao = NoteDatabase.getInstance(context).noteDao()
        val repository = NoteRepository(dao, context)
        val now = System.currentTimeMillis()
        val expired = repository.getAllWithExpiryIncludingTrashed().filter { note ->
            note.expiresAt != null && note.expiresAt <= now
        }
        expired.forEach { remove(context, repository, it) }
    }

    suspend fun purgeNote(context: Context, noteId: Long) {
        if (noteId <= 0L) return
        val dao = NoteDatabase.getInstance(context).noteDao()
        val repository = NoteRepository(dao, context)
        val note = repository.getById(noteId) ?: return
        val expiresAt = note.expiresAt ?: return
        if (expiresAt > System.currentTimeMillis()) return
        remove(context, repository, note)
    }

    private suspend fun remove(context: Context, repository: NoteRepository, note: Note) {
        extractImageFileNames(note.content).forEach { ImageStorage.deleteFile(context, it) }
        ExpiryScheduler.cancel(context, note.id)
        repository.delete(note)
    }
}
