package com.dumb.bouncynotes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteDatabase
import com.dumb.bouncynotes.data.NoteRepository
import com.dumb.bouncynotes.data.ReminderScheduler
import com.dumb.bouncynotes.data.SettingsRepository
import com.dumb.bouncynotes.data.SortOrder
import com.dumb.bouncynotes.data.StartView
import com.dumb.bouncynotes.data.extractImageFileNames
import com.dumb.bouncynotes.data.ImageStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ViewMode { ALL, TRASH, PRIVATE }

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository(
        NoteDatabase.getInstance(application).noteDao()
    )
    private val settingsRepository = SettingsRepository(application)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _viewMode = MutableStateFlow(ViewMode.ALL)
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _labelFilter = MutableStateFlow<String?>(null)
    val labelFilter: StateFlow<String?> = _labelFilter.asStateFlow()

    private val allNotes = repository.notes

    val allLabels: StateFlow<List<String>> = allNotes.map { notes ->
        notes.flatMap { it.labels }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<Note>> = combine(
        allNotes, _query, _viewMode, _labelFilter, settingsRepository.settings
    ) { list, q, mode, label, settings ->
        var filtered = when (mode) {
            ViewMode.ALL -> list.filter { it.deletedAt == null && !it.isPrivate }
            ViewMode.TRASH -> list.filter { it.deletedAt != null }
            ViewMode.PRIVATE -> list.filter { it.deletedAt == null && it.isPrivate }
        }
        if (label != null) {
            filtered = filtered.filter { label in it.labels }
        }
        if (q.isNotBlank()) {
            filtered = filtered.filter { note ->
                note.title.contains(q, ignoreCase = true) ||
                    note.content.contains(q, ignoreCase = true) ||
                    note.checklistItems.any { it.text.contains(q, ignoreCase = true) }
            }
        }
        val secondary: Comparator<Note> = when (settings.sortOrder) {
            SortOrder.UPDATED -> compareByDescending { it.updatedAt }
            SortOrder.CREATED -> compareByDescending { it.createdAt }
            SortOrder.ALPHABETICAL -> compareBy { it.title.lowercase() }
            SortOrder.COLOR -> compareBy { it.color ?: "zzzzzz" }
        }
        filtered.sortedWith(compareByDescending<Note> { it.pinned }.then(secondary))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val initial = settingsRepository.settings.first()
            if (initial.startView == StartView.LAST_USED) {
                _viewMode.value = runCatching { ViewMode.valueOf(initial.lastViewMode) }.getOrDefault(ViewMode.ALL)
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                if (settings.trashPurgeDays > 0) {
                    val threshold = System.currentTimeMillis() - (settings.trashPurgeDays.toLong() * 24 * 60 * 60 * 1000)
                    repository.purgeOldTrash(threshold)
                }
            }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        _labelFilter.value = null
        viewModelScope.launch { settingsRepository.setLastViewMode(mode.name) }
    }

    fun setLabelFilter(label: String?) {
        _labelFilter.value = label
        _viewMode.value = ViewMode.ALL
    }

    suspend fun getById(id: Long): Note? = repository.getById(id)

    suspend fun getAllNotesSnapshot(): List<Note> = allNotes.first()

    fun save(note: Note, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val toSave = note.copy(updatedAt = System.currentTimeMillis())
            // Se necesita el estado ANTERIOR (antes de sobreescribirlo) para
            // saber si el recordatorio realmente cambió — ver el comentario
            // en applyReminderScheduling.
            val previous = if (note.id != 0L) repository.getById(note.id) else null
            val id = repository.save(toSave)
            val savedId = if (note.id == 0L) id else note.id
            applyReminderScheduling(toSave.copy(id = savedId), previous)
            onDone(savedId)
        }
    }

    // Programa o cancela la alarma de AlarmManager según el estado actual del
    // recordatorio de la nota. Se llama cada vez que la nota se guarda o se
    // borra, para que la alarma nunca quede desincronizada de lo que hay en
    // la base de datos.
    //
    // OJO: antes esto se ejecutaba en TODOS los guardados, hubiera cambiado
    // el recordatorio o no. Eso rompía recordatorios muy cercanos en el
    // tiempo (1-2 minutos): confirmar el recordatorio guarda y programa bien
    // la alarma, pero salir de la nota (la flecha de volver también llama a
    // save()) dispara OTRO guardado unos segundos después, que vuelve a
    // pasar por acá — y ReminderScheduler.schedule() siempre CANCELA la
    // alarma ya programada antes de decidir si la vuelve a crear. Con un
    // recordatorio lejano esos segundos de diferencia no importan; con uno a
    // 1-2 minutos, alcanzan para que este segundo guardado ya vea la hora
    // como pasada, cancele la alarma que estaba perfectamente bien programada,
    // y no la reemplace por ninguna — el recordatorio desaparece en silencio.
    // Comparar contra el estado anterior evita tocar AlarmManager para nada
    // cuando el recordatorio no cambió en este guardado en particular.
    private fun applyReminderScheduling(note: Note, previous: Note?) {
        val app = getApplication<Application>()
        val reminderChanged = previous == null ||
            previous.reminderAt != note.reminderAt ||
            previous.reminderDays != note.reminderDays ||
            previous.deletedAt != note.deletedAt
        if (!reminderChanged) return

        if (note.reminderAt != null && note.deletedAt == null) {
            ReminderScheduler.schedule(app, note)
        } else {
            ReminderScheduler.cancel(app, note.id)
        }
    }

    fun togglePin(note: Note) {
        save(note.copy(pinned = !note.pinned))
    }

    fun moveToTrash(note: Note) {
        save(note.copy(deletedAt = System.currentTimeMillis(), pinned = false))
    }

    fun restore(note: Note) {
        save(note.copy(deletedAt = null))
    }

    fun deleteForever(note: Note) {
        viewModelScope.launch {
            extractImageFileNames(note.content).forEach { ImageStorage.deleteFile(getApplication(), it) }
            ReminderScheduler.cancel(getApplication(), note.id)
            repository.delete(note)
        }
    }

    fun importNotes(notes: List<Note>) {
        viewModelScope.launch {
            notes.forEach { note ->
                val savedId = repository.save(note)
                // Las alarmas de AlarmManager no se exportan/importan (no
                // tendría sentido, son puramente locales a este dispositivo),
                // así que cualquier nota importada con un recordatorio pendiente
                // necesita que se le programe la alarma de nuevo acá.
                if (note.reminderAt != null) {
                    applyReminderScheduling(note.copy(id = savedId))
                }
            }
        }
    }

    fun setPinnedForIds(ids: Set<Long>, pinned: Boolean) {
        viewModelScope.launch {
            val all = allNotes.first()
            all.filter { it.id in ids }.forEach { note ->
                repository.save(note.copy(pinned = pinned, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun moveToTrashIds(ids: Set<Long>) {
        viewModelScope.launch {
            val all = allNotes.first()
            all.filter { it.id in ids }.forEach { note ->
                repository.save(note.copy(deletedAt = System.currentTimeMillis(), pinned = false, updatedAt = System.currentTimeMillis()))
                ReminderScheduler.cancel(getApplication(), note.id)
            }
        }
    }

    fun deleteForeverIds(ids: Set<Long>) {
        viewModelScope.launch {
            val all = allNotes.first()
            all.filter { it.id in ids }.forEach { note ->
                extractImageFileNames(note.content).forEach { ImageStorage.deleteFile(getApplication(), it) }
                ReminderScheduler.cancel(getApplication(), note.id)
                repository.delete(note)
            }
        }
    }

    fun restoreIds(ids: Set<Long>) {
        viewModelScope.launch {
            val all = allNotes.first()
            all.filter { it.id in ids }.forEach { note ->
                repository.save(note.copy(deletedAt = null, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    suspend fun getTrashedCount(): Int = allNotes.first().count { it.deletedAt != null }

    fun deleteAllTrashed() {
        viewModelScope.launch {
            val all = allNotes.first()
            all.filter { it.deletedAt != null }.forEach { note ->
                extractImageFileNames(note.content).forEach { ImageStorage.deleteFile(getApplication(), it) }
                repository.delete(note)
            }
        }
    }
}
