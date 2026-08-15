package com.example.notes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notes.data.Note
import com.example.notes.data.NoteDatabase
import com.example.notes.data.NoteRepository
import com.example.notes.data.SettingsRepository
import com.example.notes.data.SortOrder
import com.example.notes.data.StartView
import com.example.notes.data.extractImageFileNames
import com.example.notes.data.ImageStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ViewMode { ALL, ARCHIVED, TRASH, PRIVATE }

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
            ViewMode.ALL -> list.filter { it.deletedAt == null && !it.archived && !it.isPrivate }
            ViewMode.ARCHIVED -> list.filter { it.deletedAt == null && it.archived }
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
            val id = repository.save(note.copy(updatedAt = System.currentTimeMillis()))
            onDone(if (note.id == 0L) id else note.id)
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
            repository.delete(note)
        }
    }

    fun importNotes(notes: List<Note>) {
        viewModelScope.launch {
            notes.forEach { repository.save(it) }
        }
    }
}
