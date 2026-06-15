package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.data.Note
import com.example.ui.theme.Slate800
import com.example.ui.theme.ElectricBlue

@Composable
fun SidebarDrawer(
    activeCategory: String,
    onCategorySelected: (String) -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToTrash: () -> Unit,
    allNotes: List<Note>,
    onNoteClick: (Note) -> Unit,
    categoriesList: List<com.example.data.Category>,
    onAddCategory: (String) -> Unit,
    onRemoveCategory: (com.example.data.Category) -> Unit,
    onEditCategory: (com.example.data.Category, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var deepSearchQuery by remember { mutableStateOf("") }
    val categories = listOf("All") + categoriesList.map { it.name }

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<com.example.data.Category?>(null) }
    var categoryNameInput by remember { mutableStateOf("") }

    // Full-text deep search snippets return
    val matchingNotes = remember(deepSearchQuery, allNotes) {
        if (deepSearchQuery.isBlank()) emptyList()
        else {
            allNotes.filter {
                it.title.contains(deepSearchQuery, ignoreCase = true) ||
                        it.content.contains(deepSearchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .testTag("sidebar_drawer")
    ) {
        // App Header Brand
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_app_thumbnail),
                contentDescription = "SlateNotes Premium Obsidian Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "SlateNotes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "HarmonyOS Sync Enabled",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = ElectricBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Deep Full-Text Search inside notes with snippets
        Text(
            text = "DEEP FULL-TEXT SEARCH",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        OutlinedTextField(
            value = deepSearchQuery,
            onValueChange = { deepSearchQuery = it },
            placeholder = { Text("Find terms & matching snippets...", fontSize = 12.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Notes Icon",
                    tint = MaterialTheme.colorScheme.secondary
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("deep_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        // Matching deep snippets content
        AnimatedVisibility(visible = matchingNotes.isNotEmpty() || deepSearchQuery.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Text(
                    text = "SEARCH RESULTS (${matchingNotes.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                if (matchingNotes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching phrases found", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(matchingNotes) { note ->
                            // Find match snippet
                            val snippet = remember(note.content, deepSearchQuery) {
                                val index = note.content.indexOf(deepSearchQuery, ignoreCase = true)
                                if (index == -1) {
                                    note.content.take(40) + "..."
                                } else {
                                    val start = maxOf(0, index - 15)
                                    val end = minOf(note.content.length, index + deepSearchQuery.length + 20)
                                    "..." + note.content.substring(start, end).replace("\n", " ") + "..."
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                    .clickable { onNoteClick(note) }
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = note.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = snippet,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Folders Section with Dynamic Create and Manage triggers
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(
                text = "FOLDER CATEGORIES",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.size(24.dp).testTag("add_category_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add category",
                    tint = ElectricBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Dynamic categories listing
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            categories.forEach { category ->
                val isSelected = activeCategory == category
                val icon = when (category) {
                    "All" -> Icons.Default.AllInbox
                    "Personal" -> Icons.Default.Person
                    "Work" -> Icons.Default.BusinessCenter
                    "Ideas" -> Icons.Default.Lightbulb
                    else -> Icons.Default.FolderOpen
                }

                Surface(
                    onClick = { onCategorySelected(category) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF2563EB).copy(alpha = 0.15f) else Color.Transparent,
                    contentColor = if (isSelected) Color(0xFF38BDF8) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .testTag("category_item_$category")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "$category folder icon",
                            modifier = Modifier.size(18.dp),
                            tint = if (isSelected) ElectricBlue else MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = category,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        // Note count
                        val count = allNotes.count { category == "All" || it.category == category }
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) ElectricBlue else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // Edit / Delete action triggers inline for custom editable folders (not "All" or "Uncategorized")
                        if (category != "All" && category != "Uncategorized") {
                            val categoryRecord = categoriesList.find { it.name == category }
                            if (categoryRecord != null) {
                                IconButton(
                                    onClick = {
                                        editingCategory = categoryRecord
                                        categoryNameInput = categoryRecord.name
                                        showEditDialog = true
                                    },
                                    modifier = Modifier.size(24.dp).testTag("edit_category_btn_$category")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit category name",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onRemoveCategory(categoryRecord)
                                    },
                                    modifier = Modifier.size(24.dp).testTag("delete_category_btn_$category")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete folder category",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action dialog overlays for CRUD
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showAddDialog = false
                    categoryNameInput = ""
                },
                title = { Text("New Folder Category", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = categoryNameInput,
                        onValueChange = { categoryNameInput = it },
                        placeholder = { Text("E.g., Recipes, Travel...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricBlue),
                        modifier = Modifier.fillMaxWidth().testTag("add_category_input")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (categoryNameInput.isNotBlank()) {
                                onAddCategory(categoryNameInput.trim())
                            }
                            showAddDialog = false
                            categoryNameInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showAddDialog = false
                        categoryNameInput = ""
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showEditDialog && editingCategory != null) {
            val originalName = editingCategory!!.name
            AlertDialog(
                onDismissRequest = { 
                    showEditDialog = false
                    editingCategory = null
                    categoryNameInput = ""
                },
                title = { Text("Edit Folder Name", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = categoryNameInput,
                        onValueChange = { categoryNameInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricBlue),
                        modifier = Modifier.fillMaxWidth().testTag("edit_category_input")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val inputName = categoryNameInput.trim()
                            if (inputName.isNotBlank() && inputName != originalName) {
                                onEditCategory(editingCategory!!.copy(name = inputName), originalName)
                            }
                            showEditDialog = false
                            editingCategory = null
                            categoryNameInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showEditDialog = false
                        editingCategory = null
                        categoryNameInput = ""
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Extra Global Actions
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(12.dp))

        // Nav Links: Calendar Tasks Overlay
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onNavigateToCalendar() }
                .padding(vertical = 10.dp, horizontal = 12.dp)
                .testTag("nav_calendar")
        ) {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = "Calendar Icon",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Task & Reminders Calendar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Nav Links: Trash Isolated Bin State
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onNavigateToTrash() }
                .padding(vertical = 10.dp, horizontal = 12.dp)
                .testTag("nav_trash")
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Trash Icon",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Trash Bin",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.82f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Storage details / Supabase Free Sync State
        var showCloudSettingsDialog by remember { mutableStateOf(false) }
        val context = androidx.compose.ui.platform.LocalContext.current
        val isSimulatedOffline by com.example.data.SupabaseSyncManager.isSimulatedOffline.collectAsState()
        val currentStatusMessage by com.example.data.SupabaseSyncManager.syncStatusMessage.collectAsState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSimulatedOffline) Color(0xFFF59E0B).copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
                .clickable { showCloudSettingsDialog = true }
                .padding(12.dp)
                .testTag("supabase_sync_status_box")
        ) {
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isSimulatedOffline) "Sync: Offline" else "Supabase Sync",
                        fontSize = 10.sp,
                        color = if (isSimulatedOffline) Color(0xFFF59E0B) else MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSimulatedOffline) "Queued" else "Ready",
                        fontSize = 10.sp,
                        color = if (isSimulatedOffline) Color(0xFFF59E0B) else ElectricBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (isSimulatedOffline) 0.0f else 0.08f },
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    color = if (isSimulatedOffline) Color(0xFFF59E0B) else Color(0xFF2563EB),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Notes stored: ${allNotes.size}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Tap to Manage",
                        fontSize = 9.sp,
                        color = ElectricBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Supabase & Offline Sync Control Panel Dialog
        if (showCloudSettingsDialog) {
            var urlInput by remember { mutableStateOf(com.example.data.SupabaseSyncManager.getSupabaseUrl(context)) }
            var keyInput by remember { mutableStateOf(com.example.data.SupabaseSyncManager.getSupabaseKey(context)) }

            androidx.compose.ui.window.Dialog(onDismissRequest = { showCloudSettingsDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("cloud_settings_dialog")
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CloudSync,
                                contentDescription = "Cloud Icon",
                                tint = ElectricBlue,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sync Control Center",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Configure real-time Supabase backends or toggle offline capabilities seamlessly.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Connection Status Status Card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSimulatedOffline) Color(0xFFF59E0B).copy(alpha = 0.08f)
                                else Color(0xFF10B981).copy(alpha = 0.08f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSimulatedOffline) Color(0xFFF59E0B).copy(alpha = 0.2f)
                                else Color(0xFF10B981).copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isSimulatedOffline) Color(0xFFF59E0B) else Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isSimulatedOffline) "Offline Queue Active" else "Connected & Live Syncing",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSimulatedOffline) Color(0xFFD97706) else Color(0xFF047857)
                                    )
                                    Text(
                                        text = "Status: $currentStatusMessage",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Toggle Section for Simulated Network State
                        Text(
                            text = "NETWORK SIMULATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Simulate Physical Offline State", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Queue note changes locally in database", fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary)
                            }
                            Switch(
                                checked = isSimulatedOffline,
                                onCheckedChange = { checked ->
                                    com.example.data.SupabaseSyncManager.setSimulateOffline(context, checked)
                                },
                                modifier = Modifier.testTag("offline_toggle_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Credentials inputs
                        Text(
                            text = "SUPABASE CONFIGURATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Supabase Project URL", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            placeholder = { Text("https://[ref].supabase.co", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricBlue)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Anon Api Key / Secret Token", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            placeholder = { Text("eyJhbGciOi...", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricBlue)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                com.example.data.SupabaseSyncManager.saveConfiguration(context, urlInput, keyInput)
                                showCloudSettingsDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Save Credentials & Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { showCloudSettingsDialog = false },
                            modifier = Modifier.align(Alignment.End),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.outline,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}
