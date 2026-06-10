package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.Note
import com.example.ui.theme.*
import com.example.ui.util.RichTextParser
import com.example.ui.viewmodel.NoteViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: NoteViewModel,
    onNoteClick: (Note) -> Unit,
    onAddNoteClick: () -> Unit,
    onOpenDrawerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val notes by viewModel.activeNotes.collectAsState()

    val (pinnedNotes, unpinnedNotes) = remember(notes) {
        notes.partition { it.isPinned }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddNoteClick,
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .padding(bottom = 16.dp, end = 8.dp)
                    .size(56.dp)
                    .testTag("fab_add_note")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create blank note",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top search bar styled like Keep / Immersive UI
            HeaderSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.searchQuery.value = it },
                onMenuClick = onOpenDrawerClick,
                selectedCategory = activeCategory,
                notesCount = notes.size
            )

            if (notes.isEmpty()) {
                // Empty State illustration
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NoteAlt,
                                contentDescription = "Notes list icon",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching notes" else "No notes in $activeCategory",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try searching for another term" else "Tap the '+' button below to draft your first Obsidian Note",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            } else {
                // Keep Masonry Layout
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp
                ) {
                    // Pinned notes section header
                    if (pinnedNotes.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pinned Icon",
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PINNED NOTES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricBlue,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        items(pinnedNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                viewModel = viewModel,
                                onClick = { onNoteClick(note) },
                                onPinToggle = { viewModel.togglePin(note) }
                            )
                        }

                        item(span = StaggeredGridItemSpan.FullLine) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Remaining notes header
                    if (unpinnedNotes.isNotEmpty()) {
                        if (pinnedNotes.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                Text(
                                    text = "RECENT NOTES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                )
                            }
                        }

                        items(unpinnedNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                viewModel = viewModel,
                                onClick = { onNoteClick(note) },
                                onPinToggle = { viewModel.togglePin(note) }
                            )
                        }
                    }

                    item(span = StaggeredGridItemSpan.FullLine) {
                        Spacer(modifier = Modifier.height(80.dp)) // Floating Bottom navigation space
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onMenuClick: () -> Unit,
    selectedCategory: String,
    notesCount: Int
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp)
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Toggle directories drawer",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search in $selectedCategory ($notesCount)...",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp
                    )
                }
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_search_input")
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clean searched records",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                // User account indicator placeholder JD (John Doe) as requested
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "JD",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun NoteCard(
    note: Note,
    viewModel: NoteViewModel,
    onClick: () -> Unit,
    onPinToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPinned = note.isPinned
    val cardBorder = if (isPinned) {
        BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("note_item_card_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Note Title + Pin Button
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = note.title.ifBlank { "Untitled Note" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                    contentDescription = if (isPinned) "Unpin Note" else "Pin Note",
                    tint = if (isPinned) ElectricBlue else MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { onPinToggle() }
                        .padding(2.dp)
                )
            }

            // Image Thumbnail attachment if available
            val images = remember(note.imagesJson) { viewModel.parseImages(note.imagesJson) }
            if (images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = images.first(),
                    contentDescription = "Pre-attached image thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Body text markup stripped of HTML tags
            val rawPlainText = remember(note.content) {
                note.content.replace("<[^>]*>".toRegex(), "")
            }
            if (rawPlainText.isNotBlank()) {
                Text(
                    text = rawPlainText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }

            // Checklist preview row
            val checklist = remember(note.checklistJson) { viewModel.parseChecklist(note.checklistJson) }
            if (checklist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    checklist.take(3).forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (item.isChecked) Color(0xFF2563EB).copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (item.isChecked) Color(0xFF2563EB) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        RoundedCornerShape(3.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.isChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Checked",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(9.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.text,
                                fontSize = 11.sp,
                                color = if (item.isChecked) MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                style = androidx.compose.ui.text.TextStyle(
                                    textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (checklist.size > 3) {
                        Text(
                            text = "+ ${checklist.size - 3} more items",
                            fontSize = 10.sp,
                            color = ElectricBlue,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 18.dp)
                        )
                    }
                }
            }

            // PDF attachment preview row
            if (!note.pdfName.isNullOrBlank()) {
                val pdfList = note.pdfName.split(";").filter { it.isNotBlank() }
                pdfList.forEach { pdfItem ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF2E2E).copy(alpha = 0.08f))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF Embedded Asset",
                            tint = Color(0xFFFF4D4D),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = pdfItem,
                            fontSize = 10.sp,
                            color = Color(0xFFFF7E7E),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer of category badges and calendar alarms
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Folder capsule tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2563EB).copy(alpha = 0.08f))
                        .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = note.category,
                        fontSize = 9.sp,
                        color = ElectricBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Calendar Alarm Badge if reminder set
                if (!note.reminderDate.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Due reminder date badge",
                            tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = note.reminderDate,
                            fontSize = 9.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


