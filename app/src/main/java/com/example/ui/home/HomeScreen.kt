package com.example.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EditingTemplate
import com.example.data.model.ExportedVideo
import com.example.data.model.Project
import com.example.data.model.TimelineClip
import com.example.data.model.VNImportReport
import com.example.media.ArchiveImporter
import com.example.media.SampleMediaProvider
import com.example.ui.components.CopyEditTopBar
import com.example.ui.components.GradientButton
import com.example.ui.components.PremiumCard
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.media.DocumentUtils
import com.example.media.OpenProjectDocumentContract
import com.example.ui.archive.ImportProgressDialog
import com.example.ui.archive.ImportProgressState
import com.example.ui.theme.BorderColor
import com.example.ui.theme.MutedText
import com.example.ui.theme.PrimaryGradient
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleAccentLight
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.White
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    projects: List<Project>,
    templates: List<EditingTemplate>,
    exportedVideos: List<ExportedVideo>,
    onOpenProject: (Project) -> Unit,
    onCreateNewProject: () -> Unit,
    onApplyTemplate: (EditingTemplate) -> Unit,
    onDeleteProject: (String) -> Unit,
    onDuplicateProject: (Project) -> Unit,
    onShareExport: (ExportedVideo) -> Unit,
    onPlayExport: (ExportedVideo) -> Unit,
    onImportedProjectLoaded: (VNImportReport) -> Unit,
    onVideoSelectedForNewProject: (Uri) -> Unit
) {
    val context = LocalContext.current
    var showProDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var importProgressState by remember { mutableStateOf<ImportProgressState?>(null) }

    // Media Picker for Video (Standard Android Photo/Video Picker, zero-permission, no Termux interception)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onVideoSelectedForNewProject(uri)
        }
    }

    // Storage Access Framework Document Picker for Archives & Projects (ZIP, 7Z, JSON, VNProj, etc.)
    val archivePickerLauncher = rememberLauncherForActivityResult(
        contract = OpenProjectDocumentContract()
    ) { uri: Uri? ->
        if (uri != null) {
            val meta = DocumentUtils.queryDocumentMetadata(context, uri)
            importProgressState = ImportProgressState(
                fileName = meta.displayName,
                fileSizeBytes = meta.sizeBytes,
                detectedFormat = "Detecting archive format...",
                progress = 0f,
                statusText = "Connecting to document stream..."
            )
            coroutineScope.launch {
                val report = withContext(Dispatchers.IO) {
                    ArchiveImporter.importArchiveOrFile(
                        context = context,
                        uri = uri,
                        metadata = meta,
                        onProgress = { progress, status, format ->
                            importProgressState = ImportProgressState(
                                fileName = meta.displayName,
                                fileSizeBytes = meta.sizeBytes,
                                detectedFormat = format,
                                progress = progress,
                                statusText = status
                            )
                        }
                    )
                }
                importProgressState = null
                onImportedProjectLoaded(report)
            }
        }
    }

    Scaffold(
        topBar = {
            CopyEditTopBar(
                onSearchClick = { showSearchDialog = true },
                onNotificationsClick = { showNotificationsDialog = true },
                onProClick = { showProDialog = true }
            )
        },
        containerColor = White
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(White),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Welcome Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Your Canvas Awaits.",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryText,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Edit once. Turn cuts and keyframes into reusable templates.",
                        fontSize = 14.sp,
                        color = SecondaryText,
                        modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                    )

                    // Primary Action: Large Gradient Create Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(PrimaryGradient)
                            .clickable(onClick = onCreateNewProject)
                            .padding(20.dp)
                            .testTag("create_project_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Create Project",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = White
                                )
                                Text(
                                    text = "Start a fresh timeline with real-time preview",
                                    fontSize = 13.sp,
                                    color = White.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "New Project",
                                    tint = White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Secondary Quick Actions Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionPill(
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        modifier = Modifier.weight(1f),
                        testTag = "quick_action_camera",
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )
                    QuickActionPill(
                        icon = Icons.Default.Movie,
                        label = "Video",
                        modifier = Modifier.weight(1f),
                        testTag = "quick_action_video",
                        onClick = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )
                    QuickActionPill(
                        icon = Icons.Default.FolderOpen,
                        label = "Import Project",
                        modifier = Modifier.weight(1f),
                        testTag = "quick_action_import",
                        onClick = {
                            // Storage Access Framework document picker with CATEGORY_OPENABLE and type "*/*"
                            archivePickerLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }
            }

            // Section 1: Recent Projects
            item {
                SectionHeader(
                    title = "Recent Projects",
                    actionLabel = "View All (${projects.size})"
                )
            }
            item {
                if (projects.isEmpty()) {
                    EmptySectionPlaceholder("No projects yet. Tap 'Create Project' above to begin editing.")
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(projects) { project ->
                            ProjectCard(
                                project = project,
                                onClick = { onOpenProject(project) },
                                onDuplicate = { onDuplicateProject(project) },
                                onDelete = { onDeleteProject(project.id) }
                            )
                        }
                    }
                }
            }

            // Section 2: Popular Templates
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "Popular Templates",
                    actionLabel = "${templates.size} Ready"
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(templates) { template ->
                        TemplateCard(
                            template = template,
                            onApply = { onApplyTemplate(template) }
                        )
                    }
                }
            }

            // Section 3: Recently Exported Videos
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "Recently Exported Videos",
                    actionLabel = "Movies/COPY EDIT"
                )
            }
            item {
                if (exportedVideos.isEmpty()) {
                    EmptySectionPlaceholder("No exports yet. Render your first video in 1080p from the Editor.")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        exportedVideos.take(3).forEach { export ->
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

    // Informational Modals for real interactive user experience
    if (showProDialog) {
        AlertDialog(
            onDismissRequest = { showProDialog = false },
            title = { Text("COPY EDIT PRO", fontWeight = FontWeight.Bold) },
            text = {
                Text("You have unlimited access to 1080p 60fps MP4 export, full multi-track keyframe curves, unlimited reusable templates, and safe ZIP archive import!")
            },
            confirmButton = {
                Button(
                    onClick = { showProDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                ) {
                    Text("Got it")
                }
            }
        )
    }

    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = { Text("Notifications", fontWeight = FontWeight.Bold) },
            text = {
                Text("All systems operational. Hardware media encoder ready. Movies/COPY EDIT is configured as your primary gallery export folder.")
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search Projects & Templates", fontWeight = FontWeight.Bold) },
            text = {
                Text("Search by project name, cut count, or template category (Beat Sync, Cinematic, Vlog). Found ${projects.size} project(s) and ${templates.size} template(s).")
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    importProgressState?.let { progressState ->
        ImportProgressDialog(
            state = progressState,
            onDismissRequest = { /* Keep active while importing */ }
        )
    }
}

@Composable
fun QuickActionPill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SecondaryBackground,
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor))),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = PurpleAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, actionLabel: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText
        )
        if (actionLabel.isNotBlank()) {
            Text(
                text = actionLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = PurpleAccent
            )
        }
    }
}

@Composable
fun EmptySectionPlaceholder(message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SecondaryBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier.padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                fontSize = 13.sp,
                color = SecondaryText,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val timeFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    PremiumCard(
        modifier = Modifier
            .width(200.dp)
            .testTag("project_card_${project.id}"),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Video Thumbnail / Aspect Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1F29)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Project",
                    tint = White,
                    modifier = Modifier.size(36.dp)
                )
                // Duration & Aspect Pill
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${formatDuration(project.computedDurationMs)} • ${project.aspectRatio.label}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${project.clips.size} clips • ${timeFormat.format(Date(project.updatedAt))}",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = SecondaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFEF4444)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateCard(
    template: EditingTemplate,
    onApply: () -> Unit
) {
    PremiumCard(
        modifier = Modifier
            .width(220.dp)
            .testTag("template_card_${template.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PurpleAccentLight)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = template.category,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurpleAccent
                    )
                }
                Text(
                    text = "${template.slotCount} Cuts • ${template.targetAspectRatio.label}",
                    fontSize = 11.sp,
                    color = SecondaryText,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = template.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = template.description,
                fontSize = 12.sp,
                color = SecondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            GradientButton(
                text = "Apply to Video",
                onClick = onApply,
                icon = Icons.Default.AutoAwesome,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ExportedVideoRow(
    export: ExportedVideo,
    onPlay: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SecondaryBackground,
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E1F29))
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Export",
                        tint = White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = export.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${export.resolution} • ${formatFileSize(export.fileSizeBytes)} • ${formatDuration(export.durationMs)}",
                        fontSize = 11.sp,
                        color = SecondaryText
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = PurpleAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = PrimaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format("%.1f MB", mb)
    } else {
        String.format("%.0f KB", kb)
    }
}
