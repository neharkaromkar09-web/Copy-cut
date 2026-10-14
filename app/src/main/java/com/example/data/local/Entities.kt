package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val aspectRatio: String,
    val durationMs: Long
)

@Entity(tableName = "clips")
data class ClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val sourceUri: String,
    val displayName: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val timelineStartMs: Long,
    val trackIndex: Int,
    val volume: Float,
    val speed: Float,
    val keyframesJson: String // Serialized keyframes
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String,
    val targetAspectRatio: String,
    val clipsJson: String
)

@Entity(tableName = "exported_videos")
data class ExportedVideoEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val contentUri: String,
    val filePath: String,
    val resolution: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val exportedAt: Long
)
