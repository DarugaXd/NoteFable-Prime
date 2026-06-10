package com.example.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    val allActiveNotes: Flow<List<Note>> = noteDao.getAllActiveNotes()
    val trashedNotes: Flow<List<Note>> = noteDao.getTrashedNotes()

    fun getNotesByCategory(category: String): Flow<List<Note>> {
        return if (category == "All") {
            noteDao.getAllActiveNotes()
        } else {
            noteDao.getActiveNotesByCategory(category)
        }
    }

    fun searchNotes(query: String): Flow<List<Note>> {
        return noteDao.searchActiveNotes(query)
    }

    suspend fun getNoteById(id: Int): Note? {
        return noteDao.getNoteById(id)
    }

    suspend fun insertOrUpdate(note: Note): Long {
        return noteDao.insertOrUpdateNote(note)
    }

    suspend fun deletePermanently(note: Note) {
        noteDao.deleteNotePermanently(note)
    }

    suspend fun emptyTrash() {
        noteDao.emptyTrash()
    }

    suspend fun getPendingSyncNotes(): List<Note> {
        return noteDao.getPendingSyncNotes()
    }

    val allCategories: Flow<List<Category>> = noteDao.getAllCategories()

    suspend fun insertCategory(category: Category): Long {
        return noteDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: Category) {
        // First, reset all notes in this category to Uncategorized so they are not orphaned
        noteDao.resetNotesCategoryToUncategorized(category.name)
        // Then delete the category
        noteDao.deleteCategory(category)
    }

    suspend fun updateCategory(category: Category, oldName: String) {
        // First update the notes
        noteDao.updateNotesCategory(oldName, category.name)
        // Then update the category item
        noteDao.updateCategory(category)
    }
}
