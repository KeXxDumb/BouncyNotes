package com.dumb.bouncynotes.data

import android.content.Context
import com.dumb.bouncynotes.widget.PinnedNoteWidgetUpdater
import kotlinx.coroutines.flow.Flow

// `context` es OPCIONAL a propósito: ReminderScheduler.rescheduleAll() arma
// un NoteRepository propio solo para LEER (getAllWithReminders()), sin tocar
// nada — no hace falta que ese caso dispare un refresh del widget. Cuando sí
// hay un context (el caso normal, vía NoteViewModel), cualquier save/delete/
// purgeOldTrash refresca TODAS las instancias del widget "nota fijada": es
// más simple y más robusto que rastrear a mano cuál de las 10 y pico
// funciones de NoteViewModel tocó justo la nota que un widget tiene asignada
// — total, actualizar un puñado de widgets releyendo Room es barato.
class NoteRepository(private val dao: NoteDao, private val context: Context? = null) {

    val notes: Flow<List<Note>> = dao.getAll()

    suspend fun getById(id: Long): Note? = dao.getById(id)

    suspend fun save(note: Note): Long = dao.upsert(note).also { refreshPinnedNoteWidget() }

    suspend fun delete(note: Note) = dao.delete(note).also { refreshPinnedNoteWidget() }

    suspend fun purgeOldTrash(threshold: Long) = dao.purgeOldTrash(threshold).also { refreshPinnedNoteWidget() }

    suspend fun getAllWithReminders(): List<Note> = dao.getAllWithReminders()

    private fun refreshPinnedNoteWidget() {
        context?.let { PinnedNoteWidgetUpdater.refreshAll(it) }
    }
}
