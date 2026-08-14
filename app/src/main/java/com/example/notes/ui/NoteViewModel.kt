package com.example.notes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notes.data.ImageStorage
import com.example.notes.data.Note
import com.example.notes.data.NoteDatabase
import com.example.notes.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ViewMode { ALL, ARCHIVED, TRASH, PRIVATE }

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository(
        NoteDatabase.getInstance(application).noteDao()
    )

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
        allNotes, _query, _viewMode, _labelFilter
    ) { list, q, mode, label ->
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
        filtered.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            repository.purgeOldTrash(thirtyDaysAgo)
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        _labelFilter.value = null
    }

    fun setLabelFilter(label: String?) {
        _labelFilter.value = label
        _viewMode.value = ViewMode.ALL
    }

    suspend fun getById(id: Long): Note? = repository.getById(id)

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
            note.images.forEach { ImageStorage.deleteFile(it.path) }
            repository.delete(note)
        }
    }
}
