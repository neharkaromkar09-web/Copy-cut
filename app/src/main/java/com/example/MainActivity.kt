package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.data.model.AspectRatio
import com.example.data.model.EditingTemplate
import com.example.data.model.ExportedVideo
import com.example.data.model.InterpolationType
import com.example.data.model.Keyframe
import com.example.data.model.Project
import com.example.data.model.TimelineClip
import com.example.data.model.VNImportReport
import com.example.data.repository.CopyEditRepository
import com.example.media.SampleMediaProvider
import com.example.media.VideoPlayerController
import com.example.ui.archive.ImportReportDialog
import com.example.ui.editor.EditorScreen
import com.example.ui.home.HomeScreen
import com.example.ui.library.LibraryScreen
import com.example.ui.navigation.Screen
import com.example.ui.profile.ProfileScreen
import com.example.ui.templates.TemplateApplyWizard
import com.example.ui.templates.TemplatesScreen
import com.example.ui.theme.BorderColor
import com.example.ui.theme.CopyEditTheme
import com.example.ui.theme.PrimaryGradient
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleAccentLight
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.VideoViewportDark
import com.example.ui.theme.White
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = CopyEditRepository(this)

        setContent {
            CopyEditTheme {
                CopyEditApp(
                    repository = repository,
                    onShareVideo = { export ->
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(export.contentUri))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(shareIntent, "Share Exported Video"))
                        } catch (e: Exception) {
                            // If content URI cannot be shared directly, share as text link or file info
                            val textIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Exported Video: ${export.title} (Movies/COPY EDIT)")
                            }
                            startActivity(Intent.createChooser(textIntent, "Share Video Info"))
                        }
                    }
                )
            }
        }
    }
}

sealed class AppDestination {
    data class Tab(val screen: Screen) : AppDestination()
    data class Editor(val project: Project) : AppDestination()
    data class TemplateWizard(val template: EditingTemplate) : AppDestination()
}

@OptIn(UnstableApi::class)
@Composable
fun CopyEditApp(
    repository: CopyEditRepository,
    onShareVideo: (ExportedVideo) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Data streams from Room Database
    val projects by repository.allProjectsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val templates by repository.allTemplatesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val exports by repository.allExportsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Initial preset populator
    LaunchedEffect(Unit) {
        val motionUri = SampleMediaProvider.getSampleMotionUri(context)
        val portraitUri = SampleMediaProvider.getSamplePortraitUri(context)
        repository.populatePresetsIfEmpty(motionUri, portraitUri)
    }

    // Navigation Destination
    var currentDestination by remember { mutableStateOf<AppDestination>(AppDestination.Tab(Screen.Home)) }

    // Diagnostic Import Modal
    var activeImportReport by remember { mutableStateOf<VNImportReport?>(null) }

    // Exported Video Playback Modal
    var activeVideoToPlay by remember { mutableStateOf<ExportedVideo?>(null) }

    // Handle Back Press on Subscreens
    when (val dest = currentDestination) {
        is AppDestination.Editor -> {
            BackHandler {
                currentDestination = AppDestination.Tab(Screen.Home)
            }
        }
        is AppDestination.TemplateWizard -> {
            BackHandler {
                currentDestination = AppDestination.Tab(Screen.Templates)
            }
        }
        is AppDestination.Tab -> {
            if (dest.screen != Screen.Home) {
                BackHandler {
                    currentDestination = AppDestination.Tab(Screen.Home)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = White,
        bottomBar = {
            // Only show bottom navigation on primary tabs
            if (currentDestination is AppDestination.Tab) {
                val currentTab = (currentDestination as AppDestination.Tab).screen
                NavigationBar(
                    containerColor = White,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("bottom_nav_bar")
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val isSelected = currentTab.route == screen.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (screen == Screen.Create) {
                                    // Direct Create Project action
                                    val motionUri = SampleMediaProvider.getSampleMotionUri(context)
                                    val newProject = Project(
                                        id = UUID.randomUUID().toString(),
                                        title = "New Edit",
                                        aspectRatio = AspectRatio.RATIO_16_9,
                                        clips = listOf(
                                            TimelineClip(
                                                id = UUID.randomUUID().toString(),
                                                sourceUri = motionUri,
                                                displayName = "Scene 1",
                                                sourceDurationMs = 5000L,
                                                trimStartMs = 0L,
                                                trimEndMs = 5000L,
                                                timelineStartMs = 0L,
                                                keyframes = listOf(
                                                    Keyframe(timestampMs = 0L, scaleX = 1f, scaleY = 1f),
                                                    Keyframe(timestampMs = 2500L, scaleX = 1.15f, scaleY = 1.15f, interpolation = InterpolationType.EASE_IN_OUT),
                                                    Keyframe(timestampMs = 5000L, scaleX = 1f, scaleY = 1f)
                                                )
                                            )
                                        )
                                    )
                                    coroutineScope.launch {
                                        repository.saveProject(newProject)
                                        currentDestination = AppDestination.Editor(newProject)
                                    }
                                } else {
                                    currentDestination = AppDestination.Tab(screen)
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title,
                                    tint = if (isSelected) PurpleAccent else SecondaryText
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) PurpleAccent else SecondaryText
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = PurpleAccentLight
                            ),
                            modifier = Modifier.testTag("nav_${screen.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(White)
        ) {
            when (val dest = currentDestination) {
                is AppDestination.Tab -> {
                    when (dest.screen) {
                        Screen.Home -> {
                            HomeScreen(
                                projects = projects,
                                templates = templates,
                                exportedVideos = exports,
                                onOpenProject = { project ->
                                    currentDestination = AppDestination.Editor(project)
                                },
                                onCreateNewProject = {
                                    val motionUri = SampleMediaProvider.getSampleMotionUri(context)
                                    val newProject = Project(
                                        id = UUID.randomUUID().toString(),
                                        title = "New Project",
                                        aspectRatio = AspectRatio.RATIO_16_9,
                                        clips = listOf(
                                            TimelineClip(
                                                id = UUID.randomUUID().toString(),
                                                sourceUri = motionUri,
                                                displayName = "Initial Clip",
                                                sourceDurationMs = 5000L,
                                                trimStartMs = 0L,
                                                trimEndMs = 5000L,
                                                timelineStartMs = 0L
                                            )
                                        )
                                    )
                                    coroutineScope.launch {
                                        repository.saveProject(newProject)
                                        currentDestination = AppDestination.Editor(newProject)
                                    }
                                },
                                onApplyTemplate = { template ->
                                    currentDestination = AppDestination.TemplateWizard(template)
                                },
                                onDeleteProject = { projectId ->
                                    coroutineScope.launch { repository.deleteProject(projectId) }
                                },
                                onDuplicateProject = { project ->
                                    coroutineScope.launch {
                                        val dup = repository.duplicateProject(project)
                                        currentDestination = AppDestination.Editor(dup)
                                    }
                                },
                                onShareExport = onShareVideo,
                                onPlayExport = { export ->
                                    activeVideoToPlay = export
                                },
                                onImportedProjectLoaded = { report ->
                                    activeImportReport = report
                                },
                                onVideoSelectedForNewProject = { uri ->
                                    val meta = SampleMediaProvider.inspectVideo(context, uri.toString())
                                    val clipDuration = meta.durationMs.coerceAtLeast(3000L)
                                    val selectedProject = Project(
                                        id = UUID.randomUUID().toString(),
                                        title = uri.lastPathSegment?.substringAfterLast("/") ?: "Video Edit",
                                        aspectRatio = if (meta.height > meta.width) AspectRatio.RATIO_9_16 else AspectRatio.RATIO_16_9,
                                        clips = listOf(
                                            TimelineClip(
                                                id = UUID.randomUUID().toString(),
                                                sourceUri = uri.toString(),
                                                displayName = "Imported Video",
                                                sourceDurationMs = clipDuration,
                                                trimStartMs = 0L,
                                                trimEndMs = clipDuration,
                                                timelineStartMs = 0L
                                            )
                                        )
                                    )
                                    coroutineScope.launch {
                                        repository.saveProject(selectedProject)
                                        currentDestination = AppDestination.Editor(selectedProject)
                                    }
                                }
                            )
                        }

                        Screen.Templates -> {
                            TemplatesScreen(
                                templates = templates,
                                onApplyTemplate = { template ->
                                    currentDestination = AppDestination.TemplateWizard(template)
                                },
                                onDeleteTemplate = { templateId ->
                                    coroutineScope.launch { repository.deleteTemplate(templateId) }
                                }
                            )
                        }

                        Screen.Create -> {
                            // Handled in bottom nav item onClick
                        }

                        Screen.Library -> {
                            LibraryScreen(
                                projects = projects,
                                templates = templates,
                                exportedVideos = exports,
                                onOpenProject = { project ->
                                    currentDestination = AppDestination.Editor(project)
                                },
                                onRenameProject = { targetProject, newTitle ->
                                    coroutineScope.launch {
                                        repository.saveProject(targetProject.copy(title = newTitle))
                                    }
                                },
                                onDuplicateProject = { project ->
                                    coroutineScope.launch {
                                        repository.duplicateProject(project)
                                    }
                                },
                                onDeleteProject = { id ->
                                    coroutineScope.launch { repository.deleteProject(id) }
                                },
                                onApplyTemplate = { template ->
                                    currentDestination = AppDestination.TemplateWizard(template)
                                },
                                onDeleteTemplate = { id ->
                                    coroutineScope.launch { repository.deleteTemplate(id) }
                                },
                                onPlayExport = { export ->
                                    activeVideoToPlay = export
                                },
                                onShareExport = onShareVideo
                            )
                        }

                        Screen.Profile -> {
                            ProfileScreen(
                                projectCount = projects.size,
                                templateCount = templates.size,
                                exportCount = exports.size,
                                onClearCache = {
                                    try {
                                        File(context.cacheDir, "exports").deleteRecursively()
                                        File(context.cacheDir, "imported_archives").deleteRecursively()
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                    }
                }

                is AppDestination.Editor -> {
                    EditorScreen(
                        initialProject = dest.project,
                        onBack = { currentDestination = AppDestination.Tab(Screen.Home) },
                        onSaveProject = { updatedProject ->
                            coroutineScope.launch {
                                repository.saveProject(updatedProject)
                            }
                        },
                        onCreateTemplateFromProject = { newTemplate ->
                            coroutineScope.launch {
                                repository.saveTemplate(newTemplate)
                            }
                        }
                    )
                }

                is AppDestination.TemplateWizard -> {
                    TemplateApplyWizard(
                        template = dest.template,
                        onBack = { currentDestination = AppDestination.Tab(Screen.Templates) },
                        onFinishAndOpenEditor = { appliedProject ->
                            coroutineScope.launch {
                                repository.saveProject(appliedProject)
                                currentDestination = AppDestination.Editor(appliedProject)
                            }
                        }
                    )
                }
            }
        }
    }

    // Diagnostic Import Modal
    activeImportReport?.let { report ->
        ImportReportDialog(
            report = report,
            onDismiss = { activeImportReport = null },
            onOpenImportedProject = { importedProj ->
                coroutineScope.launch {
                    repository.saveProject(importedProj)
                    currentDestination = AppDestination.Editor(importedProj)
                }
            }
        )
    }

    // Exported Video Player Modal
    activeVideoToPlay?.let { export ->
        PlayExportedVideoDialog(
            export = export,
            onDismiss = { activeVideoToPlay = null },
            onShare = { onShareVideo(export) }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlayExportedVideoDialog(
    export: ExportedVideo,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerController = remember { VideoPlayerController(context, scope) }

    LaunchedEffect(export.contentUri) {
        playerController.loadMedia(export.contentUri, playWhenReady = true)
    }

    DisposableEffect(Unit) {
        onDispose {
            playerController.release()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(export.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = SecondaryText)
                }
            }
        },
        text = {
            Column {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = VideoViewportDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    if (playerController.exoPlayer != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = playerController.exoPlayer
                                    useController = true
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Resolution: ${export.resolution} • Saved in Movies/COPY EDIT",
                    fontSize = 11.sp,
                    color = SecondaryText
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Video")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
