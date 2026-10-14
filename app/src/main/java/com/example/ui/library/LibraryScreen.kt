package com.example.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EditingTemplate
import com.example.data.model.ExportedVideo
import com.example.data.model.Project
import com.example.ui.components.CopyEditTopBar
import com.example.ui.components.PremiumCard
import com.example.ui.home.ExportedVideoRow
import com.example.ui.home.formatDuration
import com.example.ui.theme.BlueAccent
import com.example.ui.theme.BorderColor
import com.example.ui.theme.PrimaryGradient
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleAccentLight
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.White

@Composable
fun LibraryScreen(
    projects: List<Project>,
    templates: List<EditingTemplate>,
    exportedVideos: List<ExportedVideo>,
    onOpenProject: (Project) -> Unit,
    onRenameProject: (project: Project, newTitle: String) -> Unit,
    onDuplicateProject: (Project) -> Unit,
    onDeleteProject: (String) -> Unit,
    onApplyTemplate: (EditingTemplate) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onPlayExport: (ExportedVideo) -> Unit,
    onShareExport: (ExportedVideo) -> Unit
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Projects (${projects.size})", "Templates (${templates.size})", "Exports (${exportedVideos.size})")

    // Rename Dialog State
    var projectToRename by remember { mutableStateOf<Project?>(null) }
    var renameInput by remember { mutableStateOf("") }

    // Delete Confirmation State
    var projectToDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CopyEditTopBar()
        },
        containerColor = White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(White)
        ) {
            Text(
                text = "Library & Storage",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = White,
                contentColor = PurpleAccent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = PurpleAccent
                    )
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == index) PurpleAccent else SecondaryText
                            )
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                // Tab 0: Projects
                0 -> {
                    if (projects.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No saved projects found.", color = SecondaryText, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(projects) { project ->
                                PremiumCard(
                                    modifier = Modifier.fillMaxWidth().testTag("library_project_${project.id}"),
                                    onClick = { onOpenProject(project) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(project.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                            Text(
                                                "${project.clips.size} clips • ${project.aspectRatio.label} • ${formatDuration(project.computedDurationMs)}",
                                                fontSize = 12.sp,
                                                color = SecondaryText,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = {
                                                projectToRename = project
                                                renameInput = project.title
                                            }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = SecondaryText, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = { onDuplicateProject(project) }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate", tint = PurpleAccent, modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = { projectToDeleteId = project.id }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab 1: Templates
                1 -> {
                    LazyColumn(
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(templates) { template ->
                            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(template.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                                        Text("${template.category} • ${template.slotCount} cuts • ${template.targetAspectRatio.label}", fontSize = 12.sp, color = SecondaryText)
                                    }
                                    Button(
                                        onClick = { onApplyTemplate(template) },
                                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                                    ) {
                                        Text("Apply", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab 2: Exported MP4s
                2 -> {
                    if (exportedVideos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No exported MP4 videos found yet.", color = SecondaryText, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(exportedVideos) { export ->
                                ExportedVideoRow(
                                    export = export,
                                    onPlay = { onPlayExport(export) },
                                    onShare = { onShareExport(export) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    projectToRename?.let { targetProject ->
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = { Text("Rename Project", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Project Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_input_field")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            onRenameProject(targetProject, renameInput.trim())
                        }
                        projectToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    projectToDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { projectToDeleteId = null },
            title = { Text("Delete Project?", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove this project and its timeline cuts from your local database.", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(id)
                        projectToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDeleteId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
