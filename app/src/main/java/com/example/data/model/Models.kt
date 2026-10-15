package com.example.data.model

import java.util.UUID

enum class AspectRatio(val label: String, val ratio: Float, val widthRatio: Int, val heightRatio: Int) {
    RATIO_16_9("16:9", 16f / 9f, 16, 9),
    RATIO_9_16("9:16", 9f / 16f, 9, 16),
    RATIO_1_1("1:1", 1f, 1, 1),
    RATIO_4_5("4:5", 4f / 5f, 4, 5);

    companion object {
        fun fromLabel(label: String?): AspectRatio {
            return entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: RATIO_16_9
        }
    }
}

enum class InterpolationType(val label: String) {
    LINEAR("Linear"),
    EASE_IN("Ease In"),
    EASE_OUT("Ease Out"),
    EASE_IN_OUT("Ease In-Out");

    companion object {
        fun fromString(value: String?): InterpolationType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.label.equals(value, ignoreCase = true) } ?: LINEAR
        }
    }
}

data class Keyframe(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = 0L,
    val posX: Float = 0f,       // -1f to 1f normalized offset
    val posY: Float = 0f,       // -1f to 1f normalized offset
    val scaleX: Float = 1f,     // 0.1f to 5f
    val scaleY: Float = 1f,     // 0.1f to 5f
    val rotation: Float = 0f,   // degrees: -360 to 360
    val opacity: Float = 1f,    // 0f to 1f
    val interpolation: InterpolationType = InterpolationType.LINEAR
)

data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val displayName: String = "Clip",
    val sourceDurationMs: Long = 5000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 5000L,
    val timelineStartMs: Long = 0L,
    val trackIndex: Int = 0,
    val volume: Float = 1.0f,
    val speed: Float = 1.0f,
    val keyframes: List<Keyframe> = emptyList()
) {
    val durationMs: Long
        get() = ((trimEndMs - trimStartMs).coerceAtLeast(0L) / speed).toLong()

    val timelineEndMs: Long
        get() = timelineStartMs + durationMs
}

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Edit",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val durationMs: Long = 0L,
    val clips: List<TimelineClip> = emptyList()
) {
    val computedDurationMs: Long
        get() = clips.maxOfOrNull { it.timelineEndMs } ?: 0L
}

data class EditingTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val category: String = "Beat Sync",
    val targetAspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val clips: List<TimelineClip> = emptyList()
) {
    val totalDurationMs: Long
        get() = clips.maxOfOrNull { it.timelineEndMs } ?: 0L

    val slotCount: Int
        get() = clips.size
}

data class ExportedVideo(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val contentUri: String,
    val filePath: String,
    val resolution: String = "1080p",
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val exportedAt: Long = System.currentTimeMillis()
)

data class VNImportReport(
    val sourceFileName: String,
    val isArchive: Boolean,
    val fileSizeBytes: Long = 0L,
    val detectedFormat: String = if (isArchive) "Archive Package" else "Direct Project",
    val recognizedClips: Int,
    val recognizedCuts: Int,
    val recognizedKeyframes: Int,
    val recognizedAudioTracks: Int,
    val supportedEffects: List<String>,
    val unsupportedEffects: List<String>,
    val warnings: List<String>,
    val importedProject: Project?
)
