package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isInTrash = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllActiveNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isInTrash = 1 ORDER BY updatedAt DESC")
    fun getTrashedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isInTrash = 0 AND category = :category ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveNotesByCategory(category: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Int): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateNote(note: Note): Long

    @Delete
    suspend fun deleteNotePermanently(note: Note)

    @Query("DELETE FROM notes WHERE isInTrash = 1")
    suspend fun emptyTrash()

    @Query("SELECT * FROM notes WHERE isInTrash = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY isPinned DESC, updatedAt DESC")
    fun searchActiveNotes(query: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE syncState = 'PENDING_SYNC'")
    suspend fun getPendingSyncNotes(): List<Note>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Delete
    suspend fun deleteCategory(category: Category)

    @Update
    suspend fun updateCategory(category: Category)

    @Query("UPDATE notes SET category = :newName WHERE category = :oldName")
    suspend fun updateNotesCategory(oldName: String, newName: String)

    @Query("UPDATE notes SET category = 'Uncategorized' WHERE category = :categoryName")
    suspend fun resetNotesCategoryToUncategorized(categoryName: String)
}
