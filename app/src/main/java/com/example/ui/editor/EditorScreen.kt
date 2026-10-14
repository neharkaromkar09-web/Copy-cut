package com.example.ui.editor

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.data.model.AspectRatio
import com.example.data.model.EditingTemplate
import com.example.data.model.InterpolationType
import com.example.data.model.Keyframe
import com.example.data.model.Project
import com.example.data.model.TimelineClip
import com.example.media.ExportState
import com.example.media.KeyframeEngine
import com.example.media.VideoExportManager
import com.example.media.VideoPlayerController
import com.example.ui.components.GradientButton
import com.example.ui.theme.BlueAccent
import com.example.ui.theme.BlueAccentLight
import com.example.ui.theme.BorderColor
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryGradient
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleAccentLight
import com.example.ui.theme.SecondaryBackground
import com.example.ui.theme.SecondaryText
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.VideoViewportDark
import com.example.ui.theme.White
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    initialProject: Project,
    onBack: () -> Unit,
    onSaveProject: (Project) -> Unit,
    onCreateTemplateFromProject: (EditingTemplate) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Project editable state
    var project by remember { mutableStateOf(initialProject) }
    var selectedClipId by remember { mutableStateOf(initialProject.clips.firstOrNull()?.id) }

    // Undo / Redo history stack
    val undoStack = remember { mutableStateListOf<Project>() }
    val redoStack = remember { mutableStateListOf<Project>() }

    fun pushUndoState(newProject: Project) {
        undoStack.add(project)
        redoStack.clear()
        project = newProject
    }

    // Video Player Controller
    val playerController = remember { VideoPlayerController(context, coroutineScope) }
    val playerState by playerController.state.collectAsState()

    // Export Manager
    val exportManager = remember { VideoExportManager(context, coroutineScope) }
    val exportState by exportManager.exportState.collectAsState()

    // Modals
    var showExportDialog by remember { mutableStateOf(false) }
    var selectedResolution by remember { mutableStateOf("1080p") }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var templateNameInput by remember { mutableStateOf("${project.title} Template") }
    var showTransformDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSavedToast by remember { mutableStateOf(false) }

    // Active Clip & Keyframe transform
    val currentClip = project.clips.firstOrNull { it.id == selectedClipId } ?: project.clips.firstOrNull()

    // Sync media loading with active clip
    LaunchedEffect(currentClip?.sourceUri) {
        currentClip?.let { clip ->
            playerController.loadMedia(clip.sourceUri, playWhenReady = false)
        }
    }

    // Clean up player on exit
    DisposableEffect(Unit) {
        onDispose {
            playerController.release()
            exportManager.cancelExport()
        }
    }

    // Evaluated Keyframe Transform at current playback time
    val clipRelativeMs = if (currentClip != null) {
        (playerState.currentPositionMs - currentClip.trimStartMs).coerceAtLeast(0L)
    } else 0L

    val evaluatedTransform = remember(currentClip, playerState.currentPositionMs) {
        if (currentClip != null && currentClip.keyframes.isNotEmpty()) {
            KeyframeEngine.evaluate(currentClip.keyframes, clipRelativeMs)
        } else {
            com.example.media.EvaluatedTransform()
        }
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                title = project.title,
                aspectRatio = project.aspectRatio,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                onBack = onBack,
                onUndo = {
                    if (undoStack.isNotEmpty()) {
                        redoStack.add(project)
                        project = undoStack.removeAt(undoStack.lastIndex)
                    }
                },
                onRedo = {
                    if (redoStack.isNotEmpty()) {
                        undoStack.add(project)
                        project = redoStack.removeAt(redoStack.lastIndex)
                    }
                },
                onAspectRatioChange = { newRatio ->
                    pushUndoState(project.copy(aspectRatio = newRatio))
                },
                onSave = {
                    onSaveProject(project)
                    showSavedToast = true
                },
                onExportClick = { showExportDialog = true }
            )
        },
        containerColor = White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(White)
        ) {
            // Save confirmation banner
            AnimatedVisibility(visible = showSavedToast) {
                Surface(
                    color = SuccessGreen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Project saved locally to Room DB", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showSavedToast = false }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // 1. VIDEO VIEWPORT
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Viewport Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = VideoViewportDark,
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor))),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Aspect Ratio Wrapper
                        Box(
                            modifier = Modifier
                                .aspectRatio(project.aspectRatio.ratio, matchHeightConstraintsFirst = true)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            // ExoPlayer Surface with Keyframe Transform Overlay
                            if (playerController.exoPlayer != null) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = playerController.exoPlayer
                                            useController = false
                                            keepScreenOn = true
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            scaleX = evaluatedTransform.scaleX
                                            scaleY = evaluatedTransform.scaleY
                                            rotationZ = evaluatedTransform.rotation
                                            translationX = evaluatedTransform.posX * 200f
                                            translationY = evaluatedTransform.posY * 200f
                                            alpha = evaluatedTransform.opacity
                                        }
                                        .testTag("video_player_surface")
                                )
                            }

                            // Buffer Loading Indicator
                            if (playerState.isLoading) {
                                CircularProgressIndicator(
                                    color = PurpleAccent,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            // Error & Retry
                            if (playerState.errorMessage != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.8f))
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = playerState.errorMessage ?: "Playback Error",
                                        color = ErrorRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { playerController.retry() },
                                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retry Playback", fontSize = 12.sp)
                                    }
                                }
                            }

                            // Live Timecode Overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${formatTimecode(playerState.currentPositionMs)} / ${formatTimecode(project.computedDurationMs.coerceAtLeast(playerState.durationMs))}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = White
                                )
                            }
                        }
                    }
                }
            }

            // 2. PLAYER TRANSPORT CONTROLS
            Surface(
                color = White,
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute & Speed
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { playerController.toggleMute() },
                            modifier = Modifier.size(36.dp).testTag("mute_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (playerState.isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                contentDescription = "Mute",
                                tint = PrimaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(SecondaryBackground)
                                .clickable { showSpeedDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("speed_selector_pill")
                        ) {
                            Text(
                                text = "${playerState.speed}x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PurpleAccent
                            )
                        }
                    }

                    // Playback Transport: Rewind 1s, Play/Pause, Forward 1s
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { playerController.seekBy(-1000L) },
                            modifier = Modifier.size(36.dp).testTag("seek_backward_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Back 1s",
                                tint = PrimaryText,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(PrimaryGradient)
                                .clickable { playerController.togglePlayPause() }
                                .testTag("play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { playerController.seekBy(1000L) },
                            modifier = Modifier.size(36.dp).testTag("seek_forward_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 1s",
                                tint = PrimaryText,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Keyframe & Transform Indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(PurpleAccentLight)
                                .clickable { showTransformDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("active_transform_pill")
                        ) {
                            Text(
                                text = "${currentClip?.keyframes?.size ?: 0} Keyframes",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PurpleAccent
                            )
                        }
                    }
                }
            }

            // 3. MULTI-TRACK TIMELINE
            Surface(
                color = SecondaryBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                TimelineView(
                    project = project,
                    currentPositionMs = playerState.currentPositionMs,
                    selectedClipId = selectedClipId,
                    onClipSelected = { clipId -> selectedClipId = clipId },
                    onSeek = { targetMs -> playerController.seekTo(targetMs) },
                    onTrimChange = { clipId, newTrimStart, newTrimEnd ->
                        val updatedClips = project.clips.map { clip ->
                            if (clip.id == clipId) {
                                clip.copy(trimStartMs = newTrimStart, trimEndMs = newTrimEnd)
                            } else clip
                        }
                        pushUndoState(project.copy(clips = recalculateTimelineOffsets(updatedClips)))
                    }
                )
            }

            // 4. ACTION TOOLBAR
            Surface(
                color = White,
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Split / Cut Action
                    EditorToolButton(
                        icon = Icons.Default.ContentCut,
                        label = "Split",
                        testTag = "action_split_cut",
                        onClick = {
                            if (currentClip != null) {
                                val splitPoint = playerState.currentPositionMs - currentClip.timelineStartMs + currentClip.trimStartMs
                                if (splitPoint > currentClip.trimStartMs + 300L && splitPoint < currentClip.trimEndMs - 300L) {
                                    val clip1 = currentClip.copy(
                                        id = UUID.randomUUID().toString(),
                                        displayName = "${currentClip.displayName} (Part 1)",
                                        trimEndMs = splitPoint
                                    )
                                    val clip2 = currentClip.copy(
                                        id = UUID.randomUUID().toString(),
                                        displayName = "${currentClip.displayName} (Part 2)",
                                        trimStartMs = splitPoint
                                    )
                                    val newClips = project.clips.flatMap {
                                        if (it.id == currentClip.id) listOf(clip1, clip2) else listOf(it)
                                    }
                                    pushUndoState(project.copy(clips = recalculateTimelineOffsets(newClips)))
                                    selectedClipId = clip2.id
                                }
                            }
                        }
                    )

                    // Add Keyframe Action
                    EditorToolButton(
                        icon = Icons.Default.LinearScale,
                        label = "+ Keyframe",
                        testTag = "action_add_keyframe",
                        onClick = {
                            if (currentClip != null) {
                                val relTime = (playerState.currentPositionMs - currentClip.timelineStartMs).coerceAtLeast(0L)
                                val newKf = Keyframe(
                                    id = UUID.randomUUID().toString(),
                                    timestampMs = relTime,
                                    scaleX = evaluatedTransform.scaleX,
                                    scaleY = evaluatedTransform.scaleY,
                                    rotation = evaluatedTransform.rotation,
                                    opacity = evaluatedTransform.opacity,
                                    interpolation = InterpolationType.EASE_IN_OUT
                                )
                                val updatedClips = project.clips.map { clip ->
                                    if (clip.id == currentClip.id) {
                                        clip.copy(keyframes = (clip.keyframes + newKf).sortedBy { it.timestampMs })
                                    } else clip
                                }
                                pushUndoState(project.copy(clips = updatedClips))
                            }
                        }
                    )

                    // Transform Controls
                    EditorToolButton(
                        icon = Icons.Default.Transform,
                        label = "Transform",
                        testTag = "action_transform",
                        onClick = { showTransformDialog = true }
                    )

                    // Speed Setting
                    EditorToolButton(
                        icon = Icons.Default.Speed,
                        label = "Speed",
                        testTag = "action_speed",
                        onClick = { showSpeedDialog = true }
                    )

                    // Create Template from Edit
                    EditorToolButton(
                        icon = Icons.Default.AutoAwesome,
                        label = "Save Template",
                        testTag = "action_save_template",
                        onClick = { showSaveTemplateDialog = true }
                    )

                    // Delete Clip
                    if (project.clips.size > 1) {
                        EditorToolButton(
                            icon = Icons.Default.Delete,
                            label = "Delete Clip",
                            testTag = "action_delete_clip",
                            tint = ErrorRed,
                            onClick = {
                                if (currentClip != null) {
                                    val remainingClips = project.clips.filter { it.id != currentClip.id }
                                    pushUndoState(project.copy(clips = recalculateTimelineOffsets(remainingClips)))
                                    selectedClipId = remainingClips.firstOrNull()?.id
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Export Confirmation & Resolution Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { if (exportState !is ExportState.Exporting) showExportDialog = false },
            title = {
                Text("Export MP4 Video", fontWeight = FontWeight.Bold, color = PrimaryText)
            },
            text = {
                Column {
                    Text("Render your edit with cuts, audio, and keyframe animations into a playable MP4 saved in Movies/COPY EDIT.", fontSize = 13.sp, color = SecondaryText)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Select Output Resolution:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { selectedResolution = "1080p" },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedResolution == "1080p") PurpleAccentLight else White
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(listOf(if (selectedResolution == "1080p") PurpleAccent else BorderColor, if (selectedResolution == "1080p") PurpleAccent else BorderColor))
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("1080p FHD", fontSize = 12.sp, color = if (selectedResolution == "1080p") PurpleAccent else PrimaryText)
                        }
                        OutlinedButton(
                            onClick = { selectedResolution = "720p" },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedResolution == "720p") PurpleAccentLight else White
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(listOf(if (selectedResolution == "720p") PurpleAccent else BorderColor, if (selectedResolution == "720p") PurpleAccent else BorderColor))
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("720p Fast", fontSize = 12.sp, color = if (selectedResolution == "720p") PurpleAccent else PrimaryText)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Export Progress in Dialog
                    when (val state = exportState) {
                        is ExportState.Exporting -> {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = PurpleAccent,
                                    trackColor = SecondaryBackground
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(state.statusMessage, fontSize = 12.sp, color = PurpleAccent, fontWeight = FontWeight.Medium)
                            }
                        }
                        is ExportState.Success -> {
                            Text("Render complete! Saved in Movies/COPY EDIT (${state.exportedVideo.resolution}).", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        is ExportState.Error -> {
                            Text(state.message, color = ErrorRed, fontSize = 12.sp)
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                if (exportState is ExportState.Exporting) {
                    TextButton(onClick = { exportManager.cancelExport() }) {
                        Text("Cancel Export", color = ErrorRed)
                    }
                } else if (exportState is ExportState.Success) {
                    Button(
                        onClick = { showExportDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                    ) {
                        Text("Done")
                    }
                } else {
                    Button(
                        onClick = {
                            exportManager.startExport(project, selectedResolution)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                        modifier = Modifier.testTag("confirm_export_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Render")
                    }
                }
            },
            dismissButton = {
                if (exportState !is ExportState.Exporting) {
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // Save Template Dialog
    if (showSaveTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showSaveTemplateDialog = false },
            title = { Text("Save as Template", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Turn this project's clip structure, duration ratios, and keyframe animations into a reusable template that can be applied to any other video.", fontSize = 13.sp, color = SecondaryText)
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = templateNameInput,
                        onValueChange = { templateNameInput = it },
                        label = { Text("Template Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("template_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val template = EditingTemplate(
                            id = UUID.randomUUID().toString(),
                            name = templateNameInput,
                            description = "Custom template with ${project.clips.size} cuts and keyframe curves.",
                            category = "Custom",
                            targetAspectRatio = project.aspectRatio,
                            clips = project.clips
                        )
                        onCreateTemplateFromProject(template)
                        showSaveTemplateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                    modifier = Modifier.testTag("save_template_confirm")
                ) {
                    Text("Save Template")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveTemplateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Transform & Keyframe Value Editor Dialog
    if (showTransformDialog && currentClip != null) {
        var scaleValue by remember { mutableFloatStateOf(evaluatedTransform.scaleX) }
        var rotationValue by remember { mutableFloatStateOf(evaluatedTransform.rotation) }
        var opacityValue by remember { mutableFloatStateOf(evaluatedTransform.opacity) }
        var selectedEasing by remember { mutableStateOf(InterpolationType.EASE_IN_OUT) }

        AlertDialog(
            onDismissRequest = { showTransformDialog = false },
            title = { Text("Keyframe & Transform Controls", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Scale: ${String.format("%.2f", scaleValue)}x", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = scaleValue,
                        onValueChange = { scaleValue = it },
                        valueRange = 0.5f..2.5f
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Rotation: ${rotationValue.toInt()}°", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = rotationValue,
                        onValueChange = { rotationValue = it },
                        valueRange = -180f..180f
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Opacity: ${(opacityValue * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = opacityValue,
                        onValueChange = { opacityValue = it },
                        valueRange = 0.0f..1.0f
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Interpolation Curve:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InterpolationType.entries.forEach { type ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedEasing == type) PurpleAccent else SecondaryBackground)
                                    .clickable { selectedEasing = type }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = type.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedEasing == type) White else PrimaryText
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val relTime = (playerState.currentPositionMs - currentClip.timelineStartMs).coerceAtLeast(0L)
                        val newKf = Keyframe(
                            id = UUID.randomUUID().toString(),
                            timestampMs = relTime,
                            scaleX = scaleValue,
                            scaleY = scaleValue,
                            rotation = rotationValue,
                            opacity = opacityValue,
                            interpolation = selectedEasing
                        )
                        val updatedClips = project.clips.map { clip ->
                            if (clip.id == currentClip.id) {
                                val filtered = clip.keyframes.filter { it.timestampMs != relTime }
                                clip.copy(keyframes = (filtered + newKf).sortedBy { it.timestampMs })
                            } else clip
                        }
                        pushUndoState(project.copy(clips = updatedClips))
                        showTransformDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                ) {
                    Text("Apply at Playhead")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransformDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Speed Setting Dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback & Clip Speed", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Adjust the active playback speed rate:", fontSize = 13.sp, color = SecondaryText)
                    Spacer(modifier = Modifier.height(12.dp))
                    listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerController.setSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            RadioButton(
                                selected = playerState.speed == speed,
                                onClick = {
                                    playerController.setSpeed(speed)
                                    showSpeedDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${speed}x ${if (speed == 1.0f) "(Standard)" else ""}", fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun EditorTopBar(
    title: String,
    aspectRatio: AspectRatio,
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAspectRatioChange: (AspectRatio) -> Unit,
    onSave: () -> Unit,
    onExportClick: () -> Unit
) {
    var ratioMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = White,
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp).testTag("editor_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    maxLines = 1,
                    modifier = Modifier.widthIn(max = 130.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Aspect Ratio Selector
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SecondaryBackground)
                            .clickable { ratioMenuExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("aspect_ratio_pill")
                    ) {
                        Text(
                            text = aspectRatio.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PurpleAccent
                        )
                    }
                    DropdownMenu(
                        expanded = ratioMenuExpanded,
                        onDismissRequest = { ratioMenuExpanded = false }
                    ) {
                        AspectRatio.entries.forEach { ratio ->
                            DropdownMenuItem(
                                text = { Text(ratio.label) },
                                onClick = {
                                    ratioMenuExpanded = false
                                    onAspectRatioChange(ratio)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(32.dp).testTag("undo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) PrimaryText else SecondaryText.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(32.dp).testTag("redo_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) PrimaryText else SecondaryText.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(onClick = onSave, modifier = Modifier.size(32.dp).testTag("save_project_button")) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = PurpleAccent, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Export Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(PrimaryGradient)
                        .clickable(onClick = onExportClick)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("editor_export_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Export", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EditorToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    testTag: String,
    tint: Color = PrimaryText,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SecondaryBackground,
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor))),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tint)
        }
    }
}

@Composable
fun TimelineView(
    project: Project,
    currentPositionMs: Long,
    selectedClipId: String?,
    onClipSelected: (String) -> Unit,
    onSeek: (Long) -> Unit,
    onTrimChange: (clipId: String, trimStartMs: Long, trimEndMs: Long) -> Unit
) {
    val totalDurationMs = project.computedDurationMs.coerceAtLeast(5000L)
    val msToPx = 0.08f // Pixel scale per ms

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(totalDurationMs) {
                detectTapGestures { offset ->
                    val seekMs = (offset.x / msToPx).toLong().coerceIn(0L, totalDurationMs)
                    onSeek(seekMs)
                }
            }
    ) {
        val totalWidthDp = (totalDurationMs * msToPx).dp.coerceAtLeast(maxWidth)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp)
        ) {
            // Millisecond Ruler
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(White)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stepMs = 1000L
                    var time = 0L
                    while (time <= totalDurationMs) {
                        val x = time * msToPx
                        drawLine(
                            color = Color(0xFFA0A0B2),
                            start = Offset(x, 0f),
                            end = Offset(x, 14f),
                            strokeWidth = 2f
                        )
                        time += stepMs
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Video Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp)
            ) {
                project.clips.forEach { clip ->
                    val isSelected = clip.id == selectedClipId
                    val clipWidthDp = (clip.durationMs * msToPx).dp.coerceAtLeast(36.dp)
                    val clipOffsetDp = (clip.timelineStartMs * msToPx).dp

                    Box(
                        modifier = Modifier
                            .offset(x = clipOffsetDp)
                            .width(clipWidthDp)
                            .height(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) PurpleAccent else BlueAccent)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) White else BorderColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onClipSelected(clip.id) }
                            .padding(6.dp)
                    ) {
                        Column {
                            Text(
                                text = clip.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = White,
                                maxLines = 1
                            )
                            Text(
                                text = "${formatTimecode(clip.durationMs)}",
                                fontSize = 9.sp,
                                color = White.copy(alpha = 0.8f)
                            )
                        }

                        // Keyframe Diamond Markers
                        clip.keyframes.forEach { kf ->
                            val kfOffsetDp = (kf.timestampMs * msToPx).dp
                            Box(
                                modifier = Modifier
                                    .offset(x = kfOffsetDp)
                                    .align(Alignment.BottomStart)
                                    .size(8.dp)
                                    .rotate(45f)
                                    .background(Color(0xFFFBBF24))
                            )
                        }
                    }
                }

                // Playhead Scrubber Line
                val playheadOffsetDp = (currentPositionMs * msToPx).dp
                Box(
                    modifier = Modifier
                        .offset(x = playheadOffsetDp)
                        .width(2.dp)
                        .fillMaxSize()
                        .background(Color(0xFFEF4444))
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopCenter)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Audio Track Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(White)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Synchronized Master Audio Track (${formatTimecode(totalDurationMs)})",
                    fontSize = 10.sp,
                    color = SecondaryText,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

fun recalculateTimelineOffsets(clips: List<TimelineClip>): List<TimelineClip> {
    var offset = 0L
    return clips.map { clip ->
        val updated = clip.copy(timelineStartMs = offset)
        offset += updated.durationMs
        updated
    }
}

fun formatTimecode(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = (ms % 1000) / 10
    return String.format("%02d:%02d.%02d", minutes, seconds, millis)
}
