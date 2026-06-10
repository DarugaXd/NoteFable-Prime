package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val isPinned: Boolean = false,
    val isInTrash: Boolean = false,
    val category: String = "Personal", // Personal, Work, Ideas, Uncategorized
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val reminderDate: String? = null, // "YYYY-MM-DD" style or null
    val checklistJson: String? = null, // Representing list of ChecklistItem
    val imagesJson: String? = null, // Semicolon separated or JSON list of URIs
    val pdfName: String? = null, // Mock PDF name (e.g. "HarmonyOS_Architecture.pdf" / "HarmonyOS_Roadmap.pdf") or null for embeds
    val remoteId: String? = null,
    val syncState: String = "SYNCED", // SYNCED, PENDING_SYNC, LOCAL_ONLY
    val shareRole: String = "OWNER", // OWNER, EDITOR, VIEWER
    val collaboratorsJson: String? = null // JSON representation of collaborators: [{"email":"jane@example.com","role":"EDITOR"}]
)

data class ChecklistItem(
    val id: String,
    val text: String,
    val isChecked: Boolean
)
