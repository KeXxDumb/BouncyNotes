package com.example.notes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notes.data.Note
import com.example.notes.data.NoteDatabase
import com.example.notes.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository(
        NoteDatabase.getInstance(application).noteDao()
    )

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val notes: StateFlow<List<Note>> = combine(repository.notes, _query) { list, q ->
        if (q.isBlank()) list
        else list.filter {
            it.title.contains(q, ignoreCase = true) || it.content.contains(q, ignoreCase = true)
        }
    }.let { flow ->
        val state = MutableStateFlow<List<Note>>(emptyList())
        viewModelScope.launch { flow.collect { state.value = it } }
        state.asStateFlow()
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    suspend fun getById(id: Long): Note? = repository.getById(id)

    fun save(id: Long, title: String, content: String, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val noteId = repository.save(
                Note(id = id, title = title, content = content, updatedAt = System.currentTimeMillis())
            )
            onDone(if (id == 0L) noteId else id)
        }
    }

    fun delete(note: Note) {
        viewModelScope.launch { repository.delete(note) }
    }
}
