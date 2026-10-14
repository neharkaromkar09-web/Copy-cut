package com.example.data.repository

import android.content.Context
import com.example.data.local.ClipEntity
import com.example.data.local.CopyEditDatabase
import com.example.data.local.ExportedVideoEntity
import com.example.data.local.ProjectEntity
import com.example.data.local.TemplateEntity
import com.example.data.model.AspectRatio
import com.example.data.model.EditingTemplate
import com.example.data.model.ExportedVideo
import com.example.data.model.InterpolationType
import com.example.data.model.Keyframe
import com.example.data.model.Project
import com.example.data.model.TimelineClip
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class CopyEditRepository(private val context: Context) {
    private val database = CopyEditDatabase.getInstance(context)
    private val projectDao = database.projectDao()
    private val clipDao = database.clipDao()
    private val templateDao = database.templateDao()
    private val exportDao = database.exportedVideoDao()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val keyframeListType = Types.newParameterizedType(List::class.java, KeyframeDto::class.java)
    private val keyframeAdapter = moshi.adapter<List<KeyframeDto>>(keyframeListType)

    private val clipListType = Types.newParameterizedType(List::class.java, ClipDto::class.java)
    private val clipAdapter = moshi.adapter<List<ClipDto>>(clipListType)

    val allProjectsFlow: Flow<List<Project>> = projectDao.getAllProjectsFlow().map { entities ->
        entities.map { entity ->
            val clips = clipDao.getClipsForProject(entity.id).map { it.toDomain() }
            entity.toDomain(clips)
        }
    }.flowOn(Dispatchers.IO)

    val allTemplatesFlow: Flow<List<EditingTemplate>> = templateDao.getAllTemplatesFlow().map { entities ->
        entities.map { it.toDomain() }
    }.flowOn(Dispatchers.IO)

    val allExportsFlow: Flow<List<ExportedVideo>> = exportDao.getAllExportsFlow().map { entities ->
        entities.map { it.toDomain() }
    }.flowOn(Dispatchers.IO)

    suspend fun getProjectById(id: String): Project? = withContext(Dispatchers.IO) {
        val entity = projectDao.getProjectById(id) ?: return@withContext null
        val clips = clipDao.getClipsForProject(id).map { it.toDomain() }
        entity.toDomain(clips)
    }

    suspend fun saveProject(project: Project) = withContext(Dispatchers.IO) {
        val projectEntity = ProjectEntity(
            id = project.id,
            title = project.title,
            createdAt = project.createdAt,
            updatedAt = System.currentTimeMillis(),
            aspectRatio = project.aspectRatio.label,
            durationMs = project.computedDurationMs
        )
        projectDao.insertOrUpdate(projectEntity)
        clipDao.deleteClipsForProject(project.id)
        val clipEntities = project.clips.map { clip ->
            ClipEntity(
                id = clip.id,
                projectId = project.id,
                sourceUri = clip.sourceUri,
                displayName = clip.displayName,
                sourceDurationMs = clip.sourceDurationMs,
                trimStartMs = clip.trimStartMs,
                trimEndMs = clip.trimEndMs,
                timelineStartMs = clip.timelineStartMs,
                trackIndex = clip.trackIndex,
                volume = clip.volume,
                speed = clip.speed,
                keyframesJson = keyframeAdapter.toJson(clip.keyframes.map { it.toDto() }) ?: "[]"
            )
        }
        clipDao.insertClips(clipEntities)
    }

    suspend fun deleteProject(id: String) = withContext(Dispatchers.IO) {
        clipDao.deleteClipsForProject(id)
        projectDao.deleteProjectById(id)
    }

    suspend fun duplicateProject(source: Project): Project = withContext(Dispatchers.IO) {
        val newId = UUID.randomUUID().toString()
        val duplicatedClips = source.clips.map { clip ->
            clip.copy(
                id = UUID.randomUUID().toString(),
                keyframes = clip.keyframes.map { it.copy(id = UUID.randomUUID().toString()) }
            )
        }
        val duplicated = source.copy(
            id = newId,
            title = "${source.title} (Copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            clips = duplicatedClips
        )
        saveProject(duplicated)
        duplicated
    }

    suspend fun saveTemplate(template: EditingTemplate) = withContext(Dispatchers.IO) {
        val clipsJson = clipAdapter.toJson(template.clips.map { it.toDto() }) ?: "[]"
        val entity = TemplateEntity(
            id = template.id,
            name = template.name,
            description = template.description,
            category = template.category,
            targetAspectRatio = template.targetAspectRatio.label,
            clipsJson = clipsJson
        )
        templateDao.insertOrUpdate(entity)
    }

    suspend fun deleteTemplate(id: String) = withContext(Dispatchers.IO) {
        templateDao.deleteTemplateById(id)
    }

    suspend fun recordExport(export: ExportedVideo) = withContext(Dispatchers.IO) {
        val entity = ExportedVideoEntity(
            id = export.id,
            projectId = export.projectId,
            title = export.title,
            contentUri = export.contentUri,
            filePath = export.filePath,
            resolution = export.resolution,
            fileSizeBytes = export.fileSizeBytes,
            durationMs = export.durationMs,
            exportedAt = export.exportedAt
        )
        exportDao.insertExport(entity)
    }

    suspend fun deleteExport(id: String) = withContext(Dispatchers.IO) {
        exportDao.deleteExportById(id)
    }

    suspend fun populatePresetsIfEmpty(sampleUriMotion: String, sampleUriPortrait: String) = withContext(Dispatchers.IO) {
        val existingTemplates = templateDao.getTemplateById("preset_beat_sync")
        if (existingTemplates == null) {
            val beatSyncClips = listOf(
                TimelineClip(
                    id = "preset_c1",
                    sourceUri = sampleUriMotion,
                    displayName = "Beat Hit 1",
                    sourceDurationMs = 5000L,
                    trimStartMs = 0L,
                    trimEndMs = 1500L,
                    timelineStartMs = 0L,
                    keyframes = listOf(
                        Keyframe(timestampMs = 0L, scaleX = 1.0f, scaleY = 1.0f, interpolation = InterpolationType.EASE_OUT),
                        Keyframe(timestampMs = 500L, scaleX = 1.25f, scaleY = 1.25f, interpolation = InterpolationType.EASE_IN),
                        Keyframe(timestampMs = 1500L, scaleX = 1.0f, scaleY = 1.0f, interpolation = InterpolationType.LINEAR)
                    )
                ),
                TimelineClip(
                    id = "preset_c2",
                    sourceUri = sampleUriMotion,
                    displayName = "Beat Hit 2",
                    sourceDurationMs = 5000L,
                    trimStartMs = 1500L,
                    trimEndMs = 3200L,
                    timelineStartMs = 1500L,
                    keyframes = listOf(
                        Keyframe(timestampMs = 0L, rotation = -5f, scaleX = 1.1f, scaleY = 1.1f, interpolation = InterpolationType.EASE_OUT),
                        Keyframe(timestampMs = 850L, rotation = 5f, scaleX = 1.15f, scaleY = 1.15f, interpolation = InterpolationType.EASE_IN),
                        Keyframe(timestampMs = 1700L, rotation = 0f, scaleX = 1.0f, scaleY = 1.0f, interpolation = InterpolationType.LINEAR)
                    )
                ),
                TimelineClip(
                    id = "preset_c3",
                    sourceUri = sampleUriMotion,
                    displayName = "Outro Drop",
                    sourceDurationMs = 5000L,
                    trimStartMs = 3200L,
                    trimEndMs = 5000L,
                    timelineStartMs = 3200L,
                    keyframes = listOf(
                        Keyframe(timestampMs = 0L, opacity = 1.0f, posX = 0f, interpolation = InterpolationType.LINEAR),
                        Keyframe(timestampMs = 1800L, opacity = 0.85f, posX = 0.1f, interpolation = InterpolationType.EASE_IN_OUT)
                    )
                )
            )

            val beatSyncTemplate = EditingTemplate(
                id = "preset_beat_sync",
                name = "Beat Sync Pop",
                description = "High-energy rhythmic cuts with zoom punch and rotation keyframes.",
                category = "Beat Sync",
                targetAspectRatio = AspectRatio.RATIO_16_9,
                clips = beatSyncClips
            )
            saveTemplate(beatSyncTemplate)

            val cinematicClips = listOf(
                TimelineClip(
                    id = "preset_cin_1",
                    sourceUri = sampleUriPortrait,
                    displayName = "Establishing Shot",
                    sourceDurationMs = 6000L,
                    trimStartMs = 0L,
                    trimEndMs = 3000L,
                    timelineStartMs = 0L,
                    keyframes = listOf(
                        Keyframe(timestampMs = 0L, scaleX = 1.0f, scaleY = 1.0f, posY = 0f, interpolation = InterpolationType.EASE_IN_OUT),
                        Keyframe(timestampMs = 3000L, scaleX = 1.2f, scaleY = 1.2f, posY = -0.05f, interpolation = InterpolationType.EASE_IN_OUT)
                    )
                ),
                TimelineClip(
                    id = "preset_cin_2",
                    sourceUri = sampleUriPortrait,
                    displayName = "Subject Focus",
                    sourceDurationMs = 6000L,
                    trimStartMs = 3000L,
                    trimEndMs = 6000L,
                    timelineStartMs = 3000L,
                    keyframes = listOf(
                        Keyframe(timestampMs = 0L, scaleX = 1.2f, scaleY = 1.2f, interpolation = InterpolationType.EASE_OUT),
                        Keyframe(timestampMs = 3000L, scaleX = 1.0f, scaleY = 1.0f, interpolation = InterpolationType.LINEAR)
                    )
                )
            )

            val cinematicTemplate = EditingTemplate(
                id = "preset_cinematic",
                name = "Cinematic Push-In",
                description = "Smooth 9:16 vertical motion push with subtle tilt and keyframe easing.",
                category = "Cinematic",
                targetAspectRatio = AspectRatio.RATIO_9_16,
                clips = cinematicClips
            )
            saveTemplate(cinematicTemplate)
        }

        // Check if projects list is empty, create an initial demo project
        val projectEntities = projectDao.getProjectById("demo_starter_project")
        if (projectEntities == null) {
            val starterProject = Project(
                id = "demo_starter_project",
                title = "Neon Motion Showcase",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                aspectRatio = AspectRatio.RATIO_16_9,
                clips = listOf(
                    TimelineClip(
                        id = "starter_clip_1",
                        sourceUri = sampleUriMotion,
                        displayName = "Neon Motion Intro",
                        sourceDurationMs = 5000L,
                        trimStartMs = 0L,
                        trimEndMs = 3000L,
                        timelineStartMs = 0L,
                        keyframes = listOf(
                            Keyframe(timestampMs = 0L, scaleX = 1.0f, scaleY = 1.0f, interpolation = InterpolationType.EASE_OUT),
                            Keyframe(timestampMs = 1500L, scaleX = 1.2f, scaleY = 1.2f, rotation = 3f, interpolation = InterpolationType.EASE_IN_OUT),
                            Keyframe(timestampMs = 3000L, scaleX = 1.0f, scaleY = 1.0f, rotation = 0f, interpolation = InterpolationType.LINEAR)
                        )
                    ),
                    TimelineClip(
                        id = "starter_clip_2",
                        sourceUri = sampleUriMotion,
                        displayName = "Neon Motion Outro",
                        sourceDurationMs = 5000L,
                        trimStartMs = 3000L,
                        trimEndMs = 5000L,
                        timelineStartMs = 3000L,
                        keyframes = listOf(
                            Keyframe(timestampMs = 0L, posX = -0.1f, opacity = 0.9f, interpolation = InterpolationType.EASE_OUT),
                            Keyframe(timestampMs = 2000L, posX = 0f, opacity = 1.0f, interpolation = InterpolationType.LINEAR)
                        )
                    )
                )
            )
            saveProject(starterProject)
        }
    }

    private fun ProjectEntity.toDomain(clips: List<TimelineClip>): Project {
        return Project(
            id = id,
            title = title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            aspectRatio = AspectRatio.fromLabel(aspectRatio),
            durationMs = durationMs,
            clips = clips
        )
    }

    private fun ClipEntity.toDomain(): TimelineClip {
        val keyframeDtos = try {
            keyframeAdapter.fromJson(keyframesJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return TimelineClip(
            id = id,
            sourceUri = sourceUri,
            displayName = displayName,
            sourceDurationMs = sourceDurationMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            timelineStartMs = timelineStartMs,
            trackIndex = trackIndex,
            volume = volume,
            speed = speed,
            keyframes = keyframeDtos.map { it.toDomain() }
        )
    }

    private fun TemplateEntity.toDomain(): EditingTemplate {
        val clipDtos = try {
            clipAdapter.fromJson(clipsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return EditingTemplate(
            id = id,
            name = name,
            description = description,
            category = category,
            targetAspectRatio = AspectRatio.fromLabel(targetAspectRatio),
            clips = clipDtos.map { it.toDomain() }
        )
    }

    private fun ExportedVideoEntity.toDomain(): ExportedVideo {
        return ExportedVideo(
            id = id,
            projectId = projectId,
            title = title,
            contentUri = contentUri,
            filePath = filePath,
            resolution = resolution,
            fileSizeBytes = fileSizeBytes,
            durationMs = durationMs,
            exportedAt = exportedAt
        )
    }
}

data class KeyframeDto(
    val id: String,
    val timestampMs: Long,
    val posX: Float,
    val posY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float,
    val opacity: Float,
    val interpolation: String
) {
    fun toDomain(): Keyframe = Keyframe(
        id = id,
        timestampMs = timestampMs,
        posX = posX,
        posY = posY,
        scaleX = scaleX,
        scaleY = scaleY,
        rotation = rotation,
        opacity = opacity,
        interpolation = InterpolationType.fromString(interpolation)
    )
}

fun Keyframe.toDto(): KeyframeDto = KeyframeDto(
    id = id,
    timestampMs = timestampMs,
    posX = posX,
    posY = posY,
    scaleX = scaleX,
    scaleY = scaleY,
    rotation = rotation,
    opacity = opacity,
    interpolation = interpolation.name
)

data class ClipDto(
    val id: String,
    val sourceUri: String,
    val displayName: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val timelineStartMs: Long,
    val trackIndex: Int,
    val volume: Float,
    val speed: Float,
    val keyframes: List<KeyframeDto>
) {
    fun toDomain(): TimelineClip = TimelineClip(
        id = id,
        sourceUri = sourceUri,
        displayName = displayName,
        sourceDurationMs = sourceDurationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        timelineStartMs = timelineStartMs,
        trackIndex = trackIndex,
        volume = volume,
        speed = speed,
        keyframes = keyframes.map { it.toDomain() }
    )
}

fun TimelineClip.toDto(): ClipDto = ClipDto(
    id = id,
    sourceUri = sourceUri,
    displayName = displayName,
    sourceDurationMs = sourceDurationMs,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    timelineStartMs = timelineStartMs,
    trackIndex = trackIndex,
    volume = volume,
    speed = speed,
    keyframes = keyframes.map { it.toDto() }
)
