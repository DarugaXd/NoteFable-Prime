package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.animation.Crossfade
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.data.ChecklistItem
import com.example.data.Note
import com.example.ui.theme.*
import com.example.ui.util.RichTextParser
import com.example.ui.viewmodel.NoteViewModel
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    viewModel: NoteViewModel,
    noteId: Int?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // Observe note loading
    var note by remember { mutableStateOf<Note?>(null) }
    LaunchedEffect(noteId) {
        if (noteId != null) {
            val fetched = viewModel.parseChecklist(null) // Mock check
            val existing = viewModel.activeNotes.value.find { it.id == noteId }
                ?: viewModel.trashedNotes.value.find { it.id == noteId }
            note = existing ?: Note(title = "", content = "", category = "Personal")
        } else {
            note = Note(title = "", content = "", category = "Personal")
        }
    }

    if (note == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF2563EB))
        }
        return
    }

    val currentNote = note!!

    // Editor fields
    var title by remember { mutableStateOf(currentNote.title) }
    var contentValue by remember { mutableStateOf(TextFieldValue(currentNote.content)) }
    var category by remember { mutableStateOf(currentNote.category) }
    var isPinned by remember { mutableStateOf(currentNote.isPinned) }
    var reminderDate by remember { mutableStateOf(currentNote.reminderDate) }
    var pdfNames by remember {
        mutableStateOf(viewModel.parseImages(currentNote.pdfName))
    }
    var activePdfForViewer by remember { mutableStateOf<String?>(null) }
    var isEditing by remember {
        mutableStateOf(currentNote.title.isEmpty() && currentNote.content.isEmpty())
    }

    // Checklist state
    var checklistItems by remember {
        mutableStateOf(viewModel.parseChecklist(currentNote.checklistJson))
    }
    // Image list state
    var imageItems by remember {
        mutableStateOf(viewModel.parseImages(currentNote.imagesJson))
    }

    // Tabs: 0 -> Rich Editor, 1 -> Live Mode Preview
    var selectedTab by remember { mutableStateOf(0) }

    // Dialog overlays
    var showReminderDialog by remember { mutableStateOf(false) }
    var showPdfEmbedDialog by remember { mutableStateOf(false) }
    var showImageAttachmentDialog by remember { mutableStateOf(false) }
    var showSyncCollabDialog by remember { mutableStateOf(false) }

    // Collaboration and Sync State flows
    val liveCollaborators by viewModel.activeCollaborators.collectAsState()
    val syncStatusState by viewModel.syncStatus.collectAsState()
    val isOnlineState by viewModel.isOnline.collectAsState()
    val categoriesList by viewModel.categories.collectAsState()

    // Observe live database and live socket updates
    val liveNoteList by viewModel.activeNotes.collectAsState()
    val liveNote = remember(liveNoteList, currentNote.id) {
        liveNoteList.find { it.id == currentNote.id }
    }

    LaunchedEffect(liveNote?.updatedAt) {
        if (liveNote != null && liveNote.updatedAt > currentNote.updatedAt) {
            if (liveNote.content != contentValue.text) {
                contentValue = TextFieldValue(
                    text = liveNote.content,
                    selection = TextRange(liveNote.content.length)
                )
            }
            if (liveNote.title != title) {
                title = liveNote.title
            }
            if (liveNote.category != category) {
                category = liveNote.category
            }
            checklistItems = viewModel.parseChecklist(liveNote.checklistJson)
            imageItems = viewModel.parseImages(liveNote.imagesJson)
            pdfNames = viewModel.parseImages(liveNote.pdfName)
        }
    }

    // Connect to actual WebSocket endpoints if credentials exist
    LaunchedEffect(currentNote.id) {
        if (currentNote.id > 0) {
            com.example.data.SupabaseSyncManager.connectRealtime(context, currentNote.id) { rTitle, rContent ->
                if (rTitle != title) {
                    title = rTitle
                }
                if (rContent != contentValue.text) {
                    contentValue = TextFieldValue(
                        text = rContent,
                        selection = TextRange(rContent.length)
                    )
                }
            }
        }
    }

    // Keyboard scroll state
    val scrollState = rememberScrollState()

    // Autosave helper
    val saveNote = {
        val updatedNote = currentNote.copy(
            title = title,
            content = contentValue.text,
            category = category,
            isPinned = isPinned,
            reminderDate = reminderDate,
            checklistJson = viewModel.serializeChecklist(checklistItems),
            imagesJson = viewModel.serializeImages(imageItems),
            pdfName = viewModel.serializeImages(pdfNames),
            updatedAt = System.currentTimeMillis()
        )
        viewModel.insertOrUpdateNote(updatedNote) { newId ->
            if (noteId == null && note?.id == 0) {
                note = updatedNote.copy(id = newId.toInt())
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            imageItems = imageItems + uri.toString()
            saveNote()
        }
    }

    DisposableEffect(title, contentValue.text, category, isPinned, reminderDate, checklistItems, imageItems, pdfNames) {
        onDispose {
            saveNote()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    IconButton(onClick = {
                        saveNote()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Return to Dashboard",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2563EB).copy(alpha = 0.08f))
                            .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .clickable {
                                isEditing = true
                                // Simple rotate folder category
                                val folders = categoriesList.map { it.name }.ifEmpty { listOf("Personal", "Work", "Ideas", "Uncategorized") }
                                val currentIndex = folders.indexOf(category)
                                category = folders[if (currentIndex == -1) 0 else (currentIndex + 1) % folders.size]
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Active collection",
                                tint = ElectricBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(category, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Completed edit / start edit toggle
                    IconButton(
                        onClick = {
                            isEditing = !isEditing
                            if (!isEditing) {
                                saveNote()
                            }
                        },
                        modifier = Modifier.testTag("editor_completed_editing_toggle")
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = if (isEditing) "Toggle edit/reading view" else "Start writing",
                            tint = if (isEditing) Color(0xFF10B981) else ElectricBlue
                        )
                    }

                    // Calendar Reminder setting
                    IconButton(onClick = {
                        isEditing = true
                        showReminderDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Add Calendar reminder date",
                            tint = if (!reminderDate.isNullOrEmpty()) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Share Text menu option
                    IconButton(onClick = {
                        saveNote()
                        val textToShare = contentValue.text
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_SUBJECT, title)
                            putExtra(Intent.EXTRA_TEXT, "TITLE: $title\n\n$textToShare")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Export Note as Text"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export as text.txt",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Export to Formatted PDF option
                    IconButton(onClick = {
                        saveNote()
                        com.example.ui.util.PdfExporter.exportNoteToPdf(
                            context = context,
                            note = currentNote.copy(title = title, content = contentValue.text, category = category, updatedAt = System.currentTimeMillis()),
                            checklistItems = checklistItems
                        )
                    }, modifier = Modifier.testTag("editor_export_pdf_button")) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "Export to PDF",
                            tint = ElectricBlue
                        )
                    }

                    // Live Collaboration and Permissions config
                    IconButton(
                        onClick = { showSyncCollabDialog = true },
                        modifier = Modifier.testTag("editor_collab_config")
                    ) {
                        Box {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = "Manage Real-time Collaborators",
                                tint = if (liveCollaborators.isNotEmpty()) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (liveCollaborators.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }

                    // Trash Note
                    if (!currentNote.isInTrash) {
                        IconButton(onClick = {
                            viewModel.moveToTrash(currentNote)
                            onBackClick()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Discard note to trash bin",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Stats indicator bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val wordCount = contentValue.text.split("\\s+".toRegex()).count { it.isNotBlank() }
                    val charCount = contentValue.text.length
                    val readingTimeMin = maxOf(1, (wordCount / 200))
                    Text(
                        text = "$wordCount words • $charCount characters • ~$readingTimeMin min read",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )

                    Button(
                        onClick = { saveNote() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = ElectricBlue),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(imageVector = Icons.Default.CloudQueue, contentDescription = "Sync State", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Synced to Supabase", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // Title Area
                if (isEditing) {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Note Title...", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary) },
                        textStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_title_input")
                    )
                } else {
                    Text(
                        text = if (title.isEmpty()) "Untitled Note" else title,
                        style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (title.isEmpty()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { isEditing = true })
                            }
                            .padding(vertical = 12.dp)
                            .testTag("editor_title_view")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Collaborators Row and Sync Status Banner
                AnimatedVisibility(visible = liveCollaborators.isNotEmpty() || currentNote.syncState == "PENDING_SYNC" || true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Collaborator avatars
                        if (liveCollaborators.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                liveCollaborators.forEach { collaborator ->
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(collaborator.colorHex))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = collaborator.email.take(1).uppercase(),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                // Typing status
                                val typingUser = liveCollaborators.find { it.isTyping }
                                if (typingUser != null) {
                                    Text(
                                        text = "${typingUser.email.substringBefore("@")} is typing...",
                                        fontSize = 11.sp,
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                } else {
                                    Text(
                                        text = "${liveCollaborators.size} online",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Connection / Queue Indicator badge
                        val isPending = currentNote.syncState == "PENDING_SYNC" || !isOnlineState
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isPending) Color(0xFFF59E0B).copy(alpha = 0.12f) else Color(0xFF10B981).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, if (isPending) Color(0xFFF59E0B).copy(alpha = 0.3f) else Color(0xFF10B981).copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isPending) Color(0xFFF59E0B) else Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPending) "Queue: Offline" else "Cloud Synced",
                                    fontSize = 9.sp,
                                    color = if (isPending) Color(0xFFF59E0B) else Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // WYSIWYG note text area with HtmlVisualTransformation / Beautiful read viewer
                if (isEditing) {
                    OutlinedTextField(
                        value = contentValue,
                        onValueChange = { contentValue = it },
                        placeholder = { Text("Note workspace starts here. Just click here and type immediately...", fontSize = 14.sp) },
                        textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface),
                        visualTransformation = HtmlVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 350.dp)
                            .testTag("editor_body_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                } else {
                    val parsedText = remember(contentValue.text) {
                        try {
                            HtmlVisualTransformation().filter(AnnotatedString(contentValue.text)).text
                        } catch (e: Exception) {
                            AnnotatedString(contentValue.text)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 350.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { isEditing = true })
                            }
                            .padding(vertical = 8.dp)
                            .testTag("editor_body_view")
                    ) {
                        if (contentValue.text.isEmpty()) {
                            Text(
                                text = "Note workspace is empty. Double tap here to start writing...",
                                style = TextStyle(fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.secondary)
                            )
                        } else {
                            Text(
                                text = parsedText,
                                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                            )
                        }
                    }
                }



                // IMAGE & DOCUMENT ATTACHMENTS (Supabase Bucket synced)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "IMAGE & DOCUMENT ATTACHMENTS (Supabase Bucket synced)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            isEditing = true
                            showImageAttachmentDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Add Image placeholder")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Attach Image", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            isEditing = true
                            showPdfEmbedDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Add PDF placeholder")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Embed PDF Document", fontSize = 12.sp)
                    }
                }

                // Attachments horizontal render
                if (imageItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        imageItems.forEachIndexed { idx, img ->
                            Box(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = img,
                                    contentDescription = "Attached photo preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (isEditing) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .clickable {
                                                val copy = imageItems.toMutableList()
                                                copy.removeAt(idx)
                                                imageItems = copy
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete attachment", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // PDF Attachments horizontal render
                if (pdfNames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Attached Documents (${pdfNames.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pdfNames.forEachIndexed { idx, name ->
                            PdfPreviewCard(
                                pdfName = name,
                                showRemoveButton = isEditing,
                                onRemoveClick = {
                                    isEditing = true
                                    val copy = pdfNames.toMutableList()
                                    copy.removeAt(idx)
                                    pdfNames = copy
                                    saveNote()
                                },
                                onClick = {
                                    activePdfForViewer = name
                                }
                            )
                        }
                    }
                }

                // Checklist Section
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INTERACTIVE CHECKLIST",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Button(
                        onClick = {
                            isEditing = true
                            val newItem = ChecklistItem(
                                id = System.currentTimeMillis().toString(),
                                text = "",
                                isChecked = false
                            )
                            checklistItems = checklistItems + newItem
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = ElectricBlue)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Checklist item icon", modifier = Modifier.size(16.dp))
                        Text("Add Item", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (checklistItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No checklist tasks. Add checklist to organize tasks.", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    checklistItems.forEachIndexed { index, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = { isEditing = true })
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            if (isEditing) {
                                Checkbox(
                                    checked = item.isChecked,
                                    onCheckedChange = { checked ->
                                        val copy = checklistItems.toMutableList()
                                        copy[index] = item.copy(isChecked = checked)
                                        checklistItems = copy
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                OutlinedTextField(
                                    value = item.text,
                                    onValueChange = { updatedText ->
                                        val copy = checklistItems.toMutableList()
                                        copy[index] = item.copy(text = updatedText)
                                        checklistItems = copy
                                    },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None,
                                        color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp
                                    ),
                                    placeholder = { Text("Task list item...", fontSize = 12.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(
                                        onNext = {
                                            // Auto expanding checklist on ENTER!
                                            val newItem = ChecklistItem(
                                                id = System.currentTimeMillis().toString(),
                                                text = "",
                                                isChecked = false
                                            )
                                            checklistItems = checklistItems.toMutableList().apply {
                                                add(index + 1, newItem)
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElectricBlue,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        val copy = checklistItems.toMutableList()
                                        copy.removeAt(index)
                                        checklistItems = copy
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Erase item", tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                Checkbox(
                                    checked = item.isChecked,
                                    onCheckedChange = null,
                                    enabled = false
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.text.ifEmpty { "Empty Checklist Task" },
                                    style = androidx.compose.ui.text.TextStyle(
                                        textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else androidx.compose.ui.text.style.TextDecoration.None,
                                        color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(120.dp))
            }

            // Floating Bubble Menu (pops up when selection is active)
            AnimatedVisibility(
                visible = contentValue.selection.start != contentValue.selection.end,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(bottom = 32.dp)
                    .testTag("bubble_menu_overlay")
            ) {
                FloatingBubbleMenu(
                    contentValue = contentValue,
                    onContentChange = { contentValue = it }
                )
            }
        }
    }

    // Reminders & Date picker calendar overlay dialog
        if (showReminderDialog) {
            ReminderSettingsDialog(
                currentDate = reminderDate,
                onDismiss = { showReminderDialog = false },
                onDateSelected = { date ->
                    reminderDate = date
                    showReminderDialog = false
                    saveNote()
                }
            )
        }

        // PDF document embedded options chooser dialog
        if (showPdfEmbedDialog) {
            PdfEmbedOptionsDialog(
                onDismiss = { showPdfEmbedDialog = false },
                onPdfSelected = { name ->
                    if (!pdfNames.contains(name)) {
                        pdfNames = pdfNames + name
                    }
                    showPdfEmbedDialog = false
                    saveNote()
                }
            )
        }

        // Fullscreen PDF Viewer Dialog overlay
        if (activePdfForViewer != null) {
            PdfViewerDialog(
                pdfName = activePdfForViewer!!,
                onDismiss = { activePdfForViewer = null }
            )
        }

        if (showSyncCollabDialog) {
            SyncCollabDialog(
                viewModel = viewModel,
                note = currentNote,
                onDismiss = { showSyncCollabDialog = false }
            )
        }

        // Image options chooser dialog
        if (showImageAttachmentDialog) {
            ImageAttachmentDialog(
                onDismiss = { showImageAttachmentDialog = false },
                onImageSelected = { url ->
                    imageItems = imageItems + url
                    showImageAttachmentDialog = false
                    saveNote()
                },
                onPickFromGallery = {
                    showImageAttachmentDialog = false
                    galleryLauncher.launch("image/*")
                }
            )
        }
    }

@Composable
fun RichFormatToolbar(
    contentValue: TextFieldValue,
    onContentChange: (TextFieldValue) -> Unit
) {
    // Elegant formatting toolbar designed for mobile scrolling keyboards
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val formatTag = { tagOpen: String, tagClose: String ->
            val selection = contentValue.selection
            val text = contentValue.text
            val newText = if (selection.start != selection.end) {
                text.substring(0, selection.start) + tagOpen + text.substring(selection.start, selection.end) + tagClose + text.substring(selection.end)
            } else {
                text.substring(0, selection.start) + tagOpen + tagClose + text.substring(selection.start)
            }
            // Put selection cursor inside the tag
            val newCursorPos = selection.start + tagOpen.length + (if (selection.start != selection.end) (selection.end - selection.start) else 0)
            onContentChange(
                TextFieldValue(
                    text = newText,
                    selection = TextRange(newCursorPos)
                )
            )
        }

        // Bold
        IconButton(onClick = { formatTag("<b>", "</b>") }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.FormatBold, contentDescription = "Bold", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Italic
        IconButton(onClick = { formatTag("<i>", "</i>") }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.FormatItalic, contentDescription = "Italic", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Underline
        IconButton(onClick = { formatTag("<u>", "</u>") }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Strikethrough
        IconButton(onClick = { formatTag("<s>", "</s>") }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.StrikethroughS, contentDescription = "Strikethrough", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.height(24.dp))

        // Large Font
        IconButton(onClick = { formatTag("<size value=\"large\">", "</size>") }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.FormatSize, contentDescription = "Large Size", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Highlighters presets
        IconButton(onClick = { formatTag("<bg color=\"red\">", "</bg>") }, modifier = Modifier.size(36.dp)) {
            Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFEF4444)))
        }

        IconButton(onClick = { formatTag("<bg color=\"green\">", "</bg>") }, modifier = Modifier.size(36.dp)) {
            Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF10B981)))
        }

        IconButton(onClick = { formatTag("<bg color=\"blue\">", "</bg>") }, modifier = Modifier.size(36.dp)) {
            Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF3B82F6)))
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.height(24.dp))

        // Hyperlinks shortcut
        IconButton(onClick = { formatTag("<link url=\"https://huawei.com\">", "</link>") }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Link, contentDescription = "Hyperlink", tint = Color(0xFF38BDF8))
        }
    }
}

@Composable
fun PdfEmbeddedView(
    pdfName: String,
    onRemoveClick: () -> Unit
) {
    var pdfPage by remember { mutableStateOf(1) }
    val maxPages = 8

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Color(0xFFFF2E2E).copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            // Header PDF Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF Asset icon",
                        tint = Color(0xFFFF4D4D),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = pdfName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Supabase PDF Host • ${(pdfPage * 14.5).toInt()} KB / 248 KB",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Erase PDF Embed", tint = MaterialTheme.colorScheme.secondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Simulated PDF Display Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "DOCUMENT PREVIEW — PAGE $pdfPage",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricBlue
                        )
                        Text(
                            text = "SECURE HARMONYOS CONTEXT",
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (pdfPage) {
                            1 -> "SECTION 1.0 — INITIAL ARCHITECTURE OVERVIEW\nThis document details the configuration for SlateNotes to operate flawlessly on EMUI-equipped phones. Supabase Cloud API provides synchronization endpoints, making zero load over direct Google services mandatory."
                            2 -> "SECTION 2.0 — OFFLINE SYNC CAPABILITIES\nAll SQL entries queue up in safe local persistence caches. Active workers query Supabase nodes recursively when networking sockets are detected, safeguarding file changes."
                            3 -> "SECTION 3.0 — RICH MARKUP INTERPOLATIONS\nStyling is compiled into annotated components utilizing high speed rich tag regex parsers. Checked tasks are strike-through styled to map progression."
                            else -> "SECTION $pdfPage.0 — HISTORIC METADATA\nAdditional specification detailing notes folders, checklists, pin rankings, calendar dates, word count calculations, and share hooks. Synchronize to Supabase storage with zero costs."
                        },
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // PDF Control Actions (Page turners)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { if (pdfPage > 1) pdfPage-- },
                        modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Page backwards", modifier = Modifier.size(16.dp))
                    }
                    Text("$pdfPage of $maxPages", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(
                        onClick = { if (pdfPage < maxPages) pdfPage++ },
                        modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Page forwards", modifier = Modifier.size(16.dp))
                    }
                }

                // Download/Share simulated button
                Button(
                    onClick = { /* simulated */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download mock option", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Offline Save", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun ReminderSettingsDialog(
    currentDate: String?,
    onDismiss: () -> Unit,
    onDateSelected: (String?) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Calendar Alert Reminder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Assign a date to schedule this note in your calendar track.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                val presetDates = listOf("2026-06-11", "2026-06-12", "2026-06-15", "2026-06-20", "2026-06-30")
                presetDates.forEach { date ->
                    val isCurrent = currentDate == date
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) Color(0xFF2563EB).copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onDateSelected(date) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = if (isCurrent) ElectricBlue else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = date,
                            fontSize = 13.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) ElectricBlue else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { onDateSelected(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Clear Reminder", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Close", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PdfEmbedOptionsDialog(
    onDismiss: () -> Unit,
    onPdfSelected: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select Document to Embed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose from your synced Supabase bucket PDF logs.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                val pdfPresets = listOf("HarmonyOS_Next_Architecture.pdf", "Supabase_Storage_Specification.pdf", "Receipts_June_2026.pdf")
                pdfPresets.forEach { name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPdfSelected(name) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFFFF4D4D)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(name, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline, contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun ImageAttachmentDialog(
    onDismiss: () -> Unit,
    onImageSelected: (String) -> Unit,
    onPickFromGallery: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Synchronize Image Attachment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add custom images uploaded to Supabase Public Bucket or your gallery.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Device Gallery Option
                Button(
                    onClick = onPickFromGallery,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("pick_from_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Pick picture from local Gallery"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select from Gallery / Photos", fontWeight = FontWeight.Bold)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Or choose from cloud presets:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // High quality image mocks hosted publicly
                val images = listOf(
                    "https://picsum.photos/id/10/500/300",
                    "https://picsum.photos/id/29/500/300",
                    "https://picsum.photos/id/48/500/300"
                )
                val labels = listOf("Landscape Flow", "Workspace Schematic", "Obsidian Obsidian Graph")

                images.forEachIndexed { index, url ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onImageSelected(url) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(labels[index], fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline, contentColor = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun SyncCollabDialog(
    viewModel: NoteViewModel,
    note: Note,
    onDismiss: () -> Unit
) {
    var shareEmail by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf("EDITOR") } // EDITOR, VIEWER
    val liveCollaborators by viewModel.activeCollaborators.collectAsState()
    
    val currentCollaborators = remember(note.collaboratorsJson) {
        com.example.data.SupabaseSyncManager.parseCollaborators(note.collaboratorsJson)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("sync_collab_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "Share",
                        tint = ElectricBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Real-time Collaboration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Manage space sharing permissions and broadcast changes in real-time.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Invite section
                Text(
                    text = "ADD NEW COLLABORATOR",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ElectricBlue
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = shareEmail,
                    onValueChange = { shareEmail = it },
                    placeholder = { Text("collaborator@example.com", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invite_email_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Role Selector Segmented Buttons
                Text(
                    text = "ASSIGN PERMISSION ROLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val roles = listOf("EDITOR", "VIEWER")
                    roles.forEach { role ->
                        val isSelected = inviteRole == role
                        Button(
                            onClick = { inviteRole = role },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) ElectricBlue else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                text = if (role == "EDITOR") "Editor (Edit)" else "Viewer (Read)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (shareEmail.isNotBlank()) {
                            viewModel.shareNote(note.id, shareEmail.trim(), inviteRole)
                            shareEmail = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("invite_sumbit_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Space Access", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Collaborators List
                Text(
                    text = "COLLABORATORS WITH ACCESS (${currentCollaborators.size + 1})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Owner row
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(ElectricBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Y", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("You (Owner)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Full ownership & management", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                currentCollaborators.forEach { collaborator ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(collaborator.colorHex))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = collaborator.email.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(collaborator.email, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Permission: ${collaborator.role}", fontSize = 10.sp, color = ElectricBlue)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Simultaneous Editing Simulator Module
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ConnectedTv,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "SIMULATION: CO-EDITOR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Spawn a local co-author (Jane Doe) who begins typing edits into this document simultaneously to verify conflict-free real-time workspace updates.",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (liveCollaborators.isEmpty()) {
                            Button(
                                onClick = {
                                    viewModel.startCollaborationSession(note.id)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Spawn Jane (Simulate Live Typing)", fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.stopCollaborationSession()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Stop Live Typing Simulator", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.outline,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close Panel")
                }
            }
        }
    }
}

// --------------------------------------------------------------------
// REAL-TIME WYSIWYG UTILITIES & BUBBLE MENU
// --------------------------------------------------------------------

@Composable
fun FloatingBubbleMenu(
    contentValue: TextFieldValue,
    onContentChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatTag = { tagOpen: String, tagClose: String ->
        val selection = contentValue.selection
        val text = contentValue.text
        val newText = if (selection.start != selection.end) {
            text.substring(0, selection.start) + tagOpen + text.substring(selection.start, selection.end) + tagClose + text.substring(selection.end)
        } else {
            text.substring(0, selection.start) + tagOpen + tagClose + text.substring(selection.start)
        }
        val newSelection = if (selection.start != selection.end) {
            TextRange(selection.start + tagOpen.length, selection.end + tagOpen.length)
        } else {
            TextRange(selection.start + tagOpen.length)
        }
        onContentChange(
            TextFieldValue(
                text = newText,
                selection = newSelection
            )
        )
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        border = BorderStroke(1.5.dp, ElectricBlue),
        modifier = modifier.wrapContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bold
            IconButton(onClick = { formatTag("<b>", "</b>") }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.FormatBold, contentDescription = "Bold", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Italic
            IconButton(onClick = { formatTag("<i>", "</i>") }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.FormatItalic, contentDescription = "Italic", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Underline
            IconButton(onClick = { formatTag("<u>", "</u>") }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.FormatUnderlined, contentDescription = "Underline", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Strikethrough
            IconButton(onClick = { formatTag("<s>", "</s>") }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.StrikethroughS, contentDescription = "Strikethrough", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.height(24.dp))

            // Highlight colors (Red, Green, Blue)
            IconButton(onClick = { formatTag("<bg color=\"red\">", "</bg>") }, modifier = Modifier.size(32.dp)) {
                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(0xFFEF4444)))
            }

            IconButton(onClick = { formatTag("<bg color=\"green\">", "</bg>") }, modifier = Modifier.size(32.dp)) {
                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF10B981)))
            }

            IconButton(onClick = { formatTag("<bg color=\"blue\">", "</bg>") }, modifier = Modifier.size(32.dp)) {
                Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(0xFF3B82F6)))
            }

            VerticalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.height(24.dp))

            // Text Sizes (Small / Large)
            IconButton(onClick = { formatTag("<size value=\"small\">", "</size>") }, modifier = Modifier.size(36.dp)) {
                Text("a", fontWeight = FontWeight.Normal, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(onClick = { formatTag("<size value=\"large\">", "</size>") }, modifier = Modifier.size(36.dp)) {
                Text("A", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

class HtmlVisualTransformation : VisualTransformation {
    private class ActiveStyle(val startVisualIndex: Int, val tagType: String, val param: String)

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val builder = AnnotatedString.Builder()
        val visualToRawList = ArrayList<Int>()
        val rawToVisual = IntArray(raw.length + 1)
        
        var rIdx = 0
        var vIdx = 0
        
        val activeStyles = ArrayList<ActiveStyle>()
        
        while (rIdx < raw.length) {
            val char = raw[rIdx]
            if (char == '<') {
                val endTag = raw.indexOf('>', rIdx)
                if (endTag != -1) {
                    val fullTag = raw.substring(rIdx, endTag + 1)
                    val isClosing = fullTag.startsWith("</")
                    
                    if (isClosing) {
                        val cleanedTagType = fullTag.substring(2, fullTag.length - 1).trim()
                        val matchIdx = activeStyles.indexOfLast { it.tagType == cleanedTagType }
                        if (matchIdx != -1) {
                            val activeStyle = activeStyles.removeAt(matchIdx)
                            val startV = activeStyle.startVisualIndex
                            val endV = vIdx
                            if (endV > startV) {
                                when (activeStyle.tagType) {
                                    "b" -> builder.addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold), startV, endV)
                                    "i" -> builder.addStyle(androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), startV, endV)
                                    "u" -> builder.addStyle(androidx.compose.ui.text.SpanStyle(textDecoration = TextDecoration.Underline), startV, endV)
                                    "s" -> builder.addStyle(androidx.compose.ui.text.SpanStyle(textDecoration = TextDecoration.LineThrough), startV, endV)
                                    "bg" -> {
                                        val color = when (activeStyle.param) {
                                            "red" -> Color(0xFFEF4444).copy(alpha = 0.25f)
                                            "green" -> Color(0xFF10B981).copy(alpha = 0.25f)
                                            "blue" -> Color(0xFF3B82F6).copy(alpha = 0.25f)
                                            else -> Color.Transparent
                                        }
                                        builder.addStyle(androidx.compose.ui.text.SpanStyle(background = color), startV, endV)
                                    }
                                    "size" -> {
                                        val size = when (activeStyle.param) {
                                            "small" -> 11.sp
                                            "large" -> 20.sp
                                            else -> 14.sp
                                        }
                                        builder.addStyle(androidx.compose.ui.text.SpanStyle(fontSize = size), startV, endV)
                                    }
                                }
                            }
                        }
                    } else {
                        val cleanedContent = fullTag.substring(1, fullTag.length - 1)
                        if (cleanedContent.startsWith("bg ") || cleanedContent.startsWith("size ")) {
                            val firstSpace = cleanedContent.indexOf(' ')
                            val tagType = cleanedContent.substring(0, firstSpace)
                            val attrContent = cleanedContent.substring(firstSpace + 1)
                            val param = attrContent.substringAfter("=").replace("\"", "").replace("'", "").trim()
                            activeStyles.add(ActiveStyle(vIdx, tagType, param))
                        } else {
                            val tagType = cleanedContent.trim()
                            activeStyles.add(ActiveStyle(vIdx, tagType, ""))
                        }
                    }
                    
                    val tagLen = endTag - rIdx + 1
                    for (k in 0 until tagLen) {
                        if (rIdx + k < rawToVisual.size) {
                            rawToVisual[rIdx + k] = vIdx
                        }
                    }
                    rIdx += tagLen
                } else {
                    rawToVisual[rIdx] = vIdx
                    visualToRawList.add(rIdx)
                    builder.append(char)
                    vIdx++
                    rIdx++
                }
            } else {
                rawToVisual[rIdx] = vIdx
                visualToRawList.add(rIdx)
                builder.append(char)
                vIdx++
                rIdx++
            }
        }
        
        rawToVisual[raw.length] = vIdx
        visualToRawList.add(raw.length)
        
        val transformedText = builder.toAnnotatedString()
        
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, raw.length)
                return rawToVisual[clamped]
            }
            
            override fun transformedToOriginal(offset: Int): Int {
                val clamped = offset.coerceIn(0, visualToRawList.size - 1)
                return visualToRawList[clamped]
            }
        }
        
        return TransformedText(transformedText, mapping)
    }
}

@Composable
fun PdfPreviewCard(
    pdfName: String,
    showRemoveButton: Boolean = true,
    onRemoveClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(115.dp)
            .height(135.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("pdf_preview_card_$pdfName"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // The "Simple Preview Picture" area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color(0xFFFEF2F2), Color(0xFFFEE2E2))
                            )
                        )
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Draw a mini sheet of paper mimicking a PDF document page
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .border(0.5.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(4.dp)
                    ) {
                        // A red strip on top of paper representing PDF header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Draw some lines representing text
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(2.dp)
                                .background(Color(0xFFE2E8F0))
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(2.dp)
                                .background(Color(0xFFE2E8F0))
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Bottom content showing PDF name
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = pdfName,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 11.sp
                    )
                }
            }

            // Small Floating "Delete" circle in top right corner
            if (showRemoveButton) {
                IconButton(
                    onClick = { onRemoveClick() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove PDF",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PdfViewerDialog(
    pdfName: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Header with document title and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF document icon",
                            tint = Color(0xFFFF4D4D),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = pdfName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Slate PDF Document Reader",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close viewer",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // The PDF scrollable preview content
                var pdfPage by remember { mutableStateOf(1) }
                val maxPages = 8

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "PREVIEW — PAGE $pdfPage OF $maxPages",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricBlue
                            )
                            Text(
                                text = "100% OFFLINE SECURE CONTAINER",
                                fontSize = 8.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Spacer(modifier = Modifier.height(12.dp))

                        // Simulated rich content of the current PDF page
                        Text(
                            text = when (pdfName) {
                                "HarmonyOS_Next_Architecture.pdf" -> {
                                    when (pdfPage) {
                                        1 -> "SECTION 1.0 — HarmonyOS Next & ArkUI Native Core\nThis technical guide details the transition from traditional Android APIs to pure ArkTS and ArkUI controls. We configure direct microkernel pipelines optimizing layout inflation schedules, achieving 60fps rendering pipelines under high load stress."
                                        2 -> "SECTION 2.0 — Distributed Databus Interconnect\nAny local SlateNotes record written into SQL tables streams across nearby paired watches and smart tablets via soft bus clusters. Data encryption uses AES-GCM 256 for all active sync sockets."
                                        3 -> "SECTION 3.0 — Memory Reclamation Algorithms\nGarbage collection schedules align perfectly with edge transitions. Background workers pause during drag gestures and trigger immediately upon finger release, guaranteeing zero-dropped frame buffers."
                                        else -> "SECTION $pdfPage.0 — Security & Sandboxing Architecture\nAdditional parameters and capability descriptors specifying file sandboxing permissions, read-only cache hooks, zero-level system calls, and device state indicators."
                                    }
                                }
                                "Supabase_Storage_Specification.pdf" -> {
                                    when (pdfPage) {
                                        1 -> "SECTION 1.0 — Global Bucket Partitioning\nAll documents and scanned images are placed on edge servers closer to the user to keep retrieval latency low. Access controls employ JSON Web Tokens (JWT) generated directly from local authentication states."
                                        2 -> "SECTION 2.0 — Realtime Broadcast Channels\nSupabase Realtime listens to PostgreSQL WAL pipelines, translating write events into lightweight WebSocket protocol frames. SlateNotes receives incoming modifications within 40ms of edge completion."
                                        3 -> "SECTION 3.0 — Offline Resilient Synchronizer\nIf connections break, database write-ahead records stack in SQLite. A linear backoff retry engine queries Supabase gateways repeatedly until successful network status replies are received."
                                        else -> "SECTION $pdfPage.0 — Storage Compression & Scaling\nDetailed descriptions regarding auto-scaling public storage buckets, image compression ratios, custom document parsing, and backup redundancy structures."
                                    }
                                }
                                "Receipts_June_2026.pdf" -> {
                                    when (pdfPage) {
                                        1 -> "TRANSACTION RECORD: 2026-06-05\nSupabase Storage Hosting S1 (Professional Tier): $15.00 USD\nPayment Method: VISA **** 1290\nBilling Reference: TXN-817293-SUP\nStatus: PAID AND COMPLETED"
                                        2 -> "TRANSACTION RECORD: 2026-06-08\nHuawei Developer Console Subscription: $0.00 USD\nTier: Standard Individual License\nStatus: ACTIVE"
                                        3 -> "TRANSACTION RECORD: 2026-06-10\nSlateNotes Premium Sync License: $1.99 USD\nStatus: PAID AND COMPLETED"
                                        else -> "ADDITIONAL BILLING SCHEDULE — JUNE 2026\nAutomatic renewal is configured for July 12, 2026. Review payment methods in your profile dashboard settings. All premium features remain fully unlocked."
                                    }
                                }
                                else -> {
                                    when (pdfPage) {
                                        1 -> "SECTION 1.0 — CUSTOM DOCUMENT VIEW\nDocument name: $pdfName\nFile descriptor: local://documents/cache/$pdfName\nWelcome to SlateNotes Document Reader. This viewer compiles your notes data directly."
                                        else -> "SECTION $pdfPage.0 — HISTORICAL METADATA\nPreviewing custom pdf text inside SlateNotes Secure PDF sandboxed viewer. Close this dialog to return to the rich editor screen."
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Page Swiper actions inside the Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (pdfPage > 1) pdfPage-- },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Page backwards",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "$pdfPage / $maxPages",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { if (pdfPage < maxPages) pdfPage++ },
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Page forwards",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}


