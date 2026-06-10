package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChecklistItem
import com.example.data.Note
import com.example.data.NoteDatabase
import com.example.data.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository

    init {
        val database = NoteDatabase.getDatabase(application)
        repository = NoteRepository(database.noteDao())
        
        // Initialize the Supabase offline sync states
        com.example.data.SupabaseSyncManager.initialize(application)
        
        // Push any leftover pending changes on startup
        syncOfflineQueue()
    }

    val categories: StateFlow<List<com.example.data.Category>> = repository.allCategories
        .onEach { list ->
            if (list.isEmpty()) {
                viewModelScope.launch {
                    repository.insertCategory(com.example.data.Category(name = "Personal"))
                    repository.insertCategory(com.example.data.Category(name = "Work"))
                    repository.insertCategory(com.example.data.Category(name = "Ideas"))
                    repository.insertCategory(com.example.data.Category(name = "Uncategorized"))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCategory(name: String) {
        viewModelScope.launch {
            repository.insertCategory(com.example.data.Category(name = name))
        }
    }

    fun removeCategory(category: com.example.data.Category) {
        viewModelScope.launch {
            // Also reset active selection if current category is deleted
            if (selectedCategory.value == category.name) {
                selectedCategory.value = "All"
            }
            repository.deleteCategory(category)
        }
    }

    fun editCategory(category: com.example.data.Category, oldName: String) {
        viewModelScope.launch {
            if (selectedCategory.value == oldName) {
                selectedCategory.value = category.name
            }
            repository.updateCategory(category, oldName)
        }
    }

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("All")
    val selectedDate = MutableStateFlow<String?>(null) // YYYY-MM-DD

    // Combines search query and selected folding category to render live search feeds
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeNotes: StateFlow<List<Note>> = combine(
        selectedCategory,
        searchQuery
    ) { category, query ->
        Pair(category, query)
    }.flatMapLatest { (category, query) ->
        if (query.isNotBlank()) {
            repository.searchNotes(query)
        } else {
            repository.getNotesByCategory(category)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedNotes: StateFlow<List<Note>> = repository.trashedNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Flow of all reminders across notes, for the calendar visual tracker
    val notesWithReminders: StateFlow<List<Note>> = repository.allActiveNotes
        .map { list ->
            list.filter { !it.reminderDate.isNullOrEmpty() }
                .sortedBy { it.reminderDate }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertOrUpdateNote(note: Note, onComplete: ((Long) -> Unit)? = null) {
        viewModelScope.launch {
            val isOffline = com.example.data.SupabaseSyncManager.isSimulatedOffline.value
            val cleanState = if (isOffline) "PENDING_SYNC" else "SYNCED"
            val updated = note.copy(
                syncState = cleanState,
                updatedAt = System.currentTimeMillis()
            )
            val id = repository.insertOrUpdate(updated)
            
            // Broadcast editing live via WebSocket if online and note exists
            if (!isOffline) {
                val broadcastNote = if (updated.id <= 0) updated.copy(id = id.toInt()) else updated
                com.example.data.SupabaseSyncManager.broadcastNoteEdit(getApplication(), broadcastNote)
            }
            
            onComplete?.invoke(id)
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis())
            repository.insertOrUpdate(updated)
        }
    }

    fun moveToTrash(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(isInTrash = true, isPinned = false, updatedAt = System.currentTimeMillis())
            repository.insertOrUpdate(updated)
        }
    }

    fun restoreFromTrash(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(isInTrash = false, updatedAt = System.currentTimeMillis())
            repository.insertOrUpdate(updated)
        }
    }

    fun deletePermanently(note: Note) {
        viewModelScope.launch {
            repository.deletePermanently(note)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }

    // Helper functions to serialize/deserialize simple checklist elements
    fun parseChecklist(json: String?): List<ChecklistItem> {
        if (json.isNullOrBlank()) return emptyList()
        // Simple robust non-external-dependent split mechanism
        return try {
            json.split("|||")
                .filter { it.isNotBlank() }
                .map { item ->
                    val parts = item.split(":::")
                    ChecklistItem(
                        id = parts.getOrNull(0) ?: System.currentTimeMillis().toString(),
                        text = parts.getOrNull(1) ?: "",
                        isChecked = parts.getOrNull(2)?.toBoolean() ?: false
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeChecklist(list: List<ChecklistItem>): String {
        return list.joinToString("|||") { "${it.id}:::${it.text}:::${it.isChecked}" }
    }

    // Parse image list
    fun parseImages(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return json.split(";").filter { it.isNotBlank() }
    }

    fun serializeImages(list: List<String>): String {
        return list.joinToString(";")
    }

    // --- REALTIME SYNC & COLLABORATION CORE ---
    val syncStatus: StateFlow<String> = com.example.data.SupabaseSyncManager.syncStatusMessage
    val isOnline: StateFlow<Boolean> = com.example.data.SupabaseSyncManager.isOnline
    val activeCollaborators: StateFlow<List<com.example.data.SupabaseSyncManager.Collaborator>> = 
        com.example.data.SupabaseSyncManager.activeCollaborators

    // Method to trigger manual sync of the offline queue
    fun syncOfflineQueue() {
        viewModelScope.launch {
            val pendingNotes = repository.getPendingSyncNotes()
            com.example.data.SupabaseSyncManager.syncOfflineQueue(
                context = getApplication(),
                notesToSync = pendingNotes,
                onNoteSynced = { syncedNote ->
                    viewModelScope.launch {
                        repository.insertOrUpdate(syncedNote)
                    }
                }
            )
        }
    }

    // Toggle offline mode simulation
    fun setSimulateOffline(offline: Boolean) {
        com.example.data.SupabaseSyncManager.setSimulateOffline(getApplication(), offline)
        if (!offline) {
            syncOfflineQueue()
        }
    }

    // Interactive Collaboration Session typing simulator
    fun startCollaborationSession(noteId: Int) {
        viewModelScope.launch {
            val note = repository.getNoteById(noteId) ?: return@launch
            com.example.data.SupabaseSyncManager.startSimulatedCollaborationSession(
                note = note,
                viewModelScope = viewModelScope,
                onUpdate = { updatedNote ->
                    viewModelScope.launch {
                        repository.insertOrUpdate(updatedNote)
                    }
                }
            )
        }
    }

    fun stopCollaborationSession() {
        com.example.data.SupabaseSyncManager.stopSimulatedCollaborationSession()
    }

    // Share specific note and update collaborator lists
    fun shareNote(noteId: Int, collaboratorEmail: String, role: String) {
        viewModelScope.launch {
            val note = repository.getNoteById(noteId) ?: return@launch
            val currentList = com.example.data.SupabaseSyncManager.parseCollaborators(note.collaboratorsJson).toMutableList()
            
            // Check duplicates and upsert
            currentList.removeAll { it.email == collaboratorEmail }
            currentList.add(
                com.example.data.SupabaseSyncManager.Collaborator(
                    email = collaboratorEmail,
                    role = role,
                    colorHex = listOf("#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6").random()
                )
            )

            val updatedCollaboratorsJson = com.example.data.SupabaseSyncManager.serializeCollaborators(currentList)
            val updated = note.copy(
                collaboratorsJson = updatedCollaboratorsJson,
                syncState = if (com.example.data.SupabaseSyncManager.isSimulatedOffline.value) "PENDING_SYNC" else "SYNCED"
            )
            repository.insertOrUpdate(updated)

            // Trigger sync update
            if (!com.example.data.SupabaseSyncManager.isSimulatedOffline.value) {
                com.example.data.SupabaseSyncManager.syncOfflineQueue(
                    context = getApplication(),
                    notesToSync = listOf(updated),
                    onNoteSynced = { syncedNote ->
                        viewModelScope.launch {
                            repository.insertOrUpdate(syncedNote)
                        }
                    }
                )
            }
        }
    }
}
