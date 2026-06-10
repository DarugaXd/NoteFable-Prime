package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.SidebarDrawer
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.TrashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NoteViewModel
import kotlinx.coroutines.launch

sealed interface Screen {
    object Dashboard : Screen
    data class Editor(val noteId: Int?) : Screen
    object Calendar : Screen
    object Trash : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: NoteViewModel = viewModel()
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                // Intercept hardware/system back drawer buttons to return to Dashboard rather than exit
                BackHandler(enabled = currentScreen != Screen.Dashboard || drawerState.isOpen) {
                    if (drawerState.isOpen) {
                        scope.launch { drawerState.close() }
                    } else {
                        currentScreen = Screen.Dashboard
                    }
                }

                val allNotesVal by viewModel.activeNotes.collectAsState()
                val activeCategoryVal by viewModel.selectedCategory.collectAsState()
                val categoriesListVal by viewModel.categories.collectAsState()

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = currentScreen == Screen.Dashboard,
                    drawerContent = {
                        ModalDrawerSheet {
                            SidebarDrawer(
                                activeCategory = activeCategoryVal,
                                onCategorySelected = { cat ->
                                    viewModel.selectedCategory.value = cat
                                    scope.launch { drawerState.close() }
                                },
                                onNavigateToCalendar = {
                                    currentScreen = Screen.Calendar
                                    scope.launch { drawerState.close() }
                                },
                                onNavigateToTrash = {
                                    currentScreen = Screen.Trash
                                    scope.launch { drawerState.close() }
                                },
                                allNotes = allNotesVal,
                                onNoteClick = { selectedNote ->
                                    currentScreen = Screen.Editor(selectedNote.id)
                                    scope.launch { drawerState.close() }
                                },
                                categoriesList = categoriesListVal,
                                onAddCategory = { name -> viewModel.addCategory(name) },
                                onRemoveCategory = { cat -> viewModel.removeCategory(cat) },
                                onEditCategory = { cat, oldName -> viewModel.editCategory(cat, oldName) }
                            )
                        }
                    }
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (val screen = currentScreen) {
                            is Screen.Dashboard -> {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onNoteClick = { clickedNote ->
                                        currentScreen = Screen.Editor(clickedNote.id)
                                    },
                                    onAddNoteClick = {
                                        currentScreen = Screen.Editor(null)
                                    },
                                    onOpenDrawerClick = {
                                        scope.launch { drawerState.open() }
                                    }
                                )
                            }
                            is Screen.Editor -> {
                                EditorScreen(
                                    viewModel = viewModel,
                                    noteId = screen.noteId,
                                    onBackClick = {
                                        currentScreen = Screen.Dashboard
                                    }
                                )
                            }
                            is Screen.Calendar -> {
                                CalendarScreen(
                                    viewModel = viewModel,
                                    onBackClick = {
                                        currentScreen = Screen.Dashboard
                                    },
                                    onNoteClick = { clickedNote ->
                                        currentScreen = Screen.Editor(clickedNote.id)
                                    }
                                )
                            }
                            is Screen.Trash -> {
                                TrashScreen(
                                    viewModel = viewModel,
                                    onBackClick = {
                                        currentScreen = Screen.Dashboard
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
