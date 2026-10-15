package com.example.ui.templates

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.example.data.model.EditingTemplate
import com.example.data.model.Project
import com.example.data.model.TimelineClip
import androidx.activity.result.PickVisualMediaRequest
import com.example.media.DocumentUtils
import com.example.media.ExportState
import com.example.media.SampleMediaProvider
import com.example.media.VideoExportManager
import com.example.media.VideoPlayerController
import com.example.ui.components.GradientButton
import com.example.ui.components.PremiumCard
import com.example.ui.home.formatDuration
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
import java.util.UUID

@Composable
fun TemplateApplyWizard(
    template: EditingTemplate,
    onBack: () -> Unit,
    onFinishAndOpenEditor: (Project) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Step state (1: Select Video, 2: Inspect & Map, 3: Preview & Export)
    var currentStep by remember { mutableIntStateOf(1) }

    // Selected source video URI (defaults to bundled high-motion sample for instant friction-free testing)
    var selectedVideoUri by remember { mutableStateOf(SampleMediaProvider.getSampleMotionUri(context)) }
    var selectedVideoName by remember { mutableStateOf("Bundled Sample Video") }

    // Video metadata inspection
    var videoMetadata by remember {
        mutableStateOf(SampleMediaProvider.inspectVideo(context, selectedVideoUri))
    }

    // Media picker launcher (Zero-permission Android Photo/Video picker)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val meta = DocumentUtils.queryDocumentMetadata(context, uri)
            selectedVideoUri = uri.toString()
            selectedVideoName = meta.displayName.ifBlank { "Selected Video" }
            videoMetadata = SampleMediaProvider.inspectVideo(context, selectedVideoUri)
        }
    }

    // Constructed applied project
    val appliedProject = remember(template, selectedVideoUri) {
        val totalTemplateDuration = template.totalDurationMs.coerceAtLeast(1000L)
        val sourceDuration = videoMetadata.durationMs.coerceAtLeast(totalTemplateDuration)

        // Distribute template cuts across the selected video source
        val appliedClips = template.clips.mapIndexed { index, templateClip ->
            val clipDuration = templateClip.durationMs
            val calculatedStart = (index * (sourceDuration - clipDuration).coerceAtLeast(0L) / template.clips.size.coerceAtLeast(1)).coerceAtLeast(0L)
            val calculatedEnd = (calculatedStart + clipDuration).coerceAtMost(sourceDuration)

            templateClip.copy(
                id = UUID.randomUUID().toString(),
                sourceUri = selectedVideoUri,
                displayName = "Cut ${index + 1} (${templateClip.displayName})",
                sourceDurationMs = sourceDuration,
                trimStartMs = calculatedStart,
                trimEndMs = calculatedEnd,
                timelineStartMs = templateClip.timelineStartMs
            )
        }

        Project(
            id = UUID.randomUUID().toString(),
            title = "${template.name} - Applied",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            aspectRatio = template.targetAspectRatio,
            durationMs = totalTemplateDuration,
            clips = appliedClips
        )
    }

    // Player & Export controllers for Step 3
    val playerController = remember { VideoPlayerController(context, coroutineScope) }
    val exportManager = remember { VideoExportManager(context, coroutineScope) }
    val exportState by exportManager.exportState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            playerController.release()
            exportManager.cancelExport()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = White,
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Apply Template: ${template.name}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Text(
                            text = "Step $currentStep of 3 • ${template.slotCount} cuts • ${template.targetAspectRatio.label}",
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }
                }
            }
        },
        containerColor = White
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(White)
        ) {
            // Step Progress Indicator
            WizardStepBar(currentStep = currentStep)

            when (currentStep) {
                // STEP 1: Select Source Video
                1 -> {
                    StepSelectVideo(
                        template = template,
                        selectedVideoUri = selectedVideoUri,
                        selectedVideoName = selectedVideoName,
                        onSelectPhoneVideo = {
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onUseSampleMotion = {
                            selectedVideoUri = SampleMediaProvider.getSampleMotionUri(context)
                            selectedVideoName = "Bundled Neon Motion (16:9)"
                            videoMetadata = SampleMediaProvider.inspectVideo(context, selectedVideoUri)
                        },
                        onUseSamplePortrait = {
                            selectedVideoUri = SampleMediaProvider.getSamplePortraitUri(context)
                            selectedVideoName = "Bundled Cinematic Portrait (9:16)"
                            videoMetadata = SampleMediaProvider.inspectVideo(context, selectedVideoUri)
                        },
                        onNext = { currentStep = 2 }
                    )
                }

                // STEP 2: Inspect Media & Validate Cuts
                2 -> {
                    StepInspectAndMap(
                        template = template,
                        appliedProject = appliedProject,
                        videoMetadata = videoMetadata,
                        onNext = {
                            currentStep = 3
                            playerController.loadMedia(appliedProject.clips.first().sourceUri, playWhenReady = true)
                        },
                        onPrev = { currentStep = 1 }
                    )
                }

                // STEP 3: Real Preview & Export or Edit
                3 -> {
                    StepPreviewAndExport(
                        appliedProject = appliedProject,
                        playerController = playerController,
                        exportManager = exportManager,
                        exportState = exportState,
                        onOpenInEditor = { onFinishAndOpenEditor(appliedProject) },
                        onPrev = { currentStep = 2 }
                    )
                }
            }
        }
    }
}

@Composable
fun WizardStepBar(currentStep: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            "1. Source Video",
            "2. Cuts & Mapping",
            "3. Preview & Render"
        ).forEachIndexed { index, stepLabel ->
            val stepNumber = index + 1
            val isActive = currentStep == stepNumber
            val isPassed = currentStep > stepNumber

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isPassed -> SuccessGreen
                                isActive -> PurpleAccent
                                else -> BorderColor
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPassed) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = White, modifier = Modifier.size(14.dp))
                    } else {
                        Text(
                            text = "$stepNumber",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) White else SecondaryText
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stepLabel,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) PrimaryText else SecondaryText
                )
            }
        }
    }
}

@Composable
fun StepSelectVideo(
    template: EditingTemplate,
    selectedVideoUri: String,
    selectedVideoName: String,
    onSelectPhoneVideo: () -> Unit,
    onUseSampleMotion: () -> Unit,
    onUseSamplePortrait: () -> Unit,
    onNext: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Select Video to Apply Template",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Text(
                text = "COPY EDIT will analyze your footage, calculate cut timestamps, and map ${template.slotCount} clips with keyframe curves.",
                fontSize = 13.sp,
                color = SecondaryText,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Active Selection Display
        item {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Currently Selected Video:", fontSize = 12.sp, color = SecondaryText)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PurpleAccentLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VideoFile, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedVideoName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            Text(
                                text = "Ready to cut and transform",
                                fontSize = 11.sp,
                                color = SuccessGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Options: Choose from Phone vs Bundled Samples
        item {
            Text("Video Source Options:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
            Spacer(modifier = Modifier.height(8.dp))

            // Option 1: Phone Picker
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = White,
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(PurpleAccent, BlueAccent))),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSelectPhoneVideo)
                    .testTag("pick_device_video_button")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PrimaryGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Select from My Phone", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                        Text("Pick any MP4, MOV, or camera video via Storage Access Framework", fontSize = 12.sp, color = SecondaryText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Option 2: Bundled Sample Motion
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SecondaryBackground,
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor))),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUseSampleMotion)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Use Built-in Sample (16:9 Motion)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                        Text("Test rhythmic beat sync cuts immediately with 0 setup", fontSize = 11.sp, color = SecondaryText)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Option 3: Bundled Sample Portrait
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SecondaryBackground,
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor))),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUseSamplePortrait)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Use Built-in Sample (9:16 Portrait)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
                        Text("Test vertical Reels/Shorts cinematic zoom cuts", fontSize = 11.sp, color = SecondaryText)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            GradientButton(
                text = "Next: Inspect & Map Cuts",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().testTag("wizard_next_step_1")
            )
        }
    }
}

@Composable
fun StepInspectAndMap(
    template: EditingTemplate,
    appliedProject: Project,
    videoMetadata: SampleMediaProvider.VideoMetadata,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Media Inspection & Cut Mapping",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
            Text(
                text = "Verify video duration, dimensions, audio track, and mapped clip timestamps.",
                fontSize = 13.sp,
                color = SecondaryText
            )
        }

        // Inspection Diagnostic Card
        item {
            PremiumCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Source Video Telemetry", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Duration:", fontSize = 12.sp, color = SecondaryText)
                        Text(formatDuration(videoMetadata.durationMs), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Resolution:", fontSize = 12.sp, color = SecondaryText)
                        Text("${videoMetadata.width} x ${videoMetadata.height}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Audio Track:", fontSize = 12.sp, color = SecondaryText)
                        Text(if (videoMetadata.hasAudio) "Detected (AAC/PCM)" else "No Audio", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (videoMetadata.hasAudio) SuccessGreen else SecondaryText)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Target Aspect Ratio:", fontSize = 12.sp, color = SecondaryText)
                        Text(template.targetAspectRatio.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PurpleAccent)
                    }
                }
            }
        }

        // Mapped Clips Breakdown
        item {
            Text(
                text = "Calculated Template Cuts (${appliedProject.clips.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        }

        itemsIndexed(appliedProject.clips) { index, clip ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SecondaryBackground,
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(BorderColor, BorderColor))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Clip ${index + 1}: ${clip.displayName}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Text(
                            text = "Trim: ${clip.trimStartMs}ms -> ${clip.trimEndMs}ms (${clip.durationMs}ms duration)",
                            fontSize = 11.sp,
                            color = SecondaryText
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(PurpleAccentLight)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${clip.keyframes.size} Keyframes",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PurpleAccent
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPrev,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                    modifier = Modifier.weight(1.5f).testTag("wizard_next_step_2")
                ) {
                    Text("Generate Preview")
                }
            }
        }
    }
}

@Composable
fun StepPreviewAndExport(
    appliedProject: Project,
    playerController: VideoPlayerController,
    exportManager: VideoExportManager,
    exportState: ExportState,
    onOpenInEditor: () -> Unit,
    onPrev: () -> Unit
) {
    val playerState by playerController.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Real Preview & Export",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText
        )

        // Preview Viewport
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = VideoViewportDark,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (playerController.exoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = playerController.exoPlayer
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Play / Pause Overlay
                IconButton(
                    onClick = { playerController.togglePlayPause() },
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Play",
                        tint = White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Live Export Progress if rendering
        when (exportState) {
            is ExportState.Exporting -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SecondaryBackground)
                        .padding(14.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { exportState.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = PurpleAccent
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(exportState.statusMessage, fontSize = 12.sp, color = PurpleAccent, fontWeight = FontWeight.Bold)
                }
            }
            is ExportState.Success -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SuccessGreen.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(SuccessGreen, SuccessGreen))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "MP4 Successfully Exported and saved in Movies/COPY EDIT!",
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            is ExportState.Error -> {
                Text(exportState.message, color = ErrorRed, fontSize = 12.sp)
            }
            else -> {}
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action Buttons: Open in Timeline Editor OR Render Directly
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton(
                text = "Render & Export MP4 Now",
                onClick = {
                    exportManager.startExport(appliedProject, "1080p")
                },
                icon = Icons.Default.Download,
                enabled = exportState !is ExportState.Exporting,
                modifier = Modifier.fillMaxWidth().testTag("wizard_export_button")
            )

            OutlinedButton(
                onClick = onOpenInEditor,
                modifier = Modifier.fillMaxWidth().testTag("wizard_open_editor_button")
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Timeline to Fine-Tune Edits")
            }
        }
    }
}
