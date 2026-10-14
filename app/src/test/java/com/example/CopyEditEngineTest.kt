package com.example

import com.example.data.model.AspectRatio
import com.example.data.model.InterpolationType
import com.example.data.model.Keyframe
import com.example.data.model.Project
import com.example.data.model.TimelineClip
import com.example.data.repository.KeyframeDto
import com.example.data.repository.toDto
import com.example.media.KeyframeEngine
import com.example.ui.editor.recalculateTimelineOffsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CopyEditEngineTest {

    @Test
    fun testKeyframeInterpolation_exactTimestamps() {
        val kfs = listOf(
            Keyframe(timestampMs = 0L, posX = 0f, scaleX = 1f, rotation = 0f),
            Keyframe(timestampMs = 1000L, posX = 0.5f, scaleX = 2f, rotation = 90f)
        )

        val at0 = KeyframeEngine.evaluate(kfs, 0L)
        assertEquals(0f, at0.posX, 0.001f)
        assertEquals(1f, at0.scaleX, 0.001f)
        assertEquals(0f, at0.rotation, 0.001f)

        val at1000 = KeyframeEngine.evaluate(kfs, 1000L)
        assertEquals(0.5f, at1000.posX, 0.001f)
        assertEquals(2f, at1000.scaleX, 0.001f)
        assertEquals(90f, at1000.rotation, 0.001f)
    }

    @Test
    fun testKeyframeInterpolation_midpointLinear() {
        val kfs = listOf(
            Keyframe(timestampMs = 0L, posX = 0f, scaleX = 1f, rotation = 0f, interpolation = InterpolationType.LINEAR),
            Keyframe(timestampMs = 1000L, posX = 1f, scaleX = 3f, rotation = 100f, interpolation = InterpolationType.LINEAR)
        )

        val at500 = KeyframeEngine.evaluate(kfs, 500L)
        assertEquals(0.5f, at500.posX, 0.001f)
        assertEquals(2f, at500.scaleX, 0.001f)
        assertEquals(50f, at500.rotation, 0.001f)
    }

    @Test
    fun testKeyframeInterpolation_easeInAndEaseOut() {
        // At t = 0.5:
        // Linear: 0.5
        // Ease In (t^2): 0.25
        // Ease Out (1 - (1-t)^2): 0.75
        val easeIn = KeyframeEngine.applyInterpolation(0.5f, InterpolationType.EASE_IN)
        assertEquals(0.25f, easeIn, 0.001f)

        val easeOut = KeyframeEngine.applyInterpolation(0.5f, InterpolationType.EASE_OUT)
        assertEquals(0.75f, easeOut, 0.001f)
    }

    @Test
    fun testKeyframeInterpolation_clampingBoundaries() {
        val kfs = listOf(
            Keyframe(timestampMs = 500L, posX = 0.2f, scaleX = 1.5f),
            Keyframe(timestampMs = 1500L, posX = 0.8f, scaleX = 2.5f)
        )

        // Query before first keyframe
        val before = KeyframeEngine.evaluate(kfs, 100L)
        assertEquals(0.2f, before.posX, 0.001f)
        assertEquals(1.5f, before.scaleX, 0.001f)

        // Query after last keyframe
        val after = KeyframeEngine.evaluate(kfs, 3000L)
        assertEquals(0.8f, after.posX, 0.001f)
        assertEquals(2.5f, after.scaleX, 0.001f)
    }

    @Test
    fun testKeyframeInterpolation_emptyList() {
        val defaultTransform = KeyframeEngine.evaluate(emptyList(), 100L)
        assertEquals(0f, defaultTransform.posX, 0.001f)
        assertEquals(1f, defaultTransform.scaleX, 0.001f)
        assertEquals(1f, defaultTransform.scaleY, 0.001f)
        assertEquals(0f, defaultTransform.rotation, 0.001f)
        assertEquals(1f, defaultTransform.opacity, 0.001f)
    }

    @Test
    fun testTimelineMath_recalculateOffsets() {
        val clips = listOf(
            TimelineClip(id = "c1", sourceUri = "u1", trimStartMs = 0L, trimEndMs = 2000L, timelineStartMs = 0L),
            TimelineClip(id = "c2", sourceUri = "u2", trimStartMs = 500L, trimEndMs = 3500L, timelineStartMs = 0L), // duration 3000ms
            TimelineClip(id = "c3", sourceUri = "u3", trimStartMs = 0L, trimEndMs = 1000L, timelineStartMs = 0L)   // duration 1000ms
        )

        val sequenced = recalculateTimelineOffsets(clips)
        assertEquals(0L, sequenced[0].timelineStartMs)
        assertEquals(2000L, sequenced[0].timelineEndMs)

        assertEquals(2000L, sequenced[1].timelineStartMs)
        assertEquals(5000L, sequenced[1].timelineEndMs)

        assertEquals(5000L, sequenced[2].timelineStartMs)
        assertEquals(6000L, sequenced[2].timelineEndMs)

        val project = Project(clips = sequenced)
        assertEquals(6000L, project.computedDurationMs)
    }

    @Test
    fun testTimelineClip_splitCutCalculation() {
        val originalClip = TimelineClip(
            id = "c1",
            sourceUri = "test_uri",
            sourceDurationMs = 5000L,
            trimStartMs = 1000L,
            trimEndMs = 4000L,
            timelineStartMs = 0L
        )

        val splitPointMs = 2500L // 1500ms into the clip
        val part1 = originalClip.copy(trimEndMs = splitPointMs)
        val part2 = originalClip.copy(trimStartMs = splitPointMs)

        assertEquals(1500L, part1.durationMs)
        assertEquals(1500L, part2.durationMs)
        assertEquals(originalClip.durationMs, part1.durationMs + part2.durationMs)
    }

    @Test
    fun testKeyframeSerialization_domainMapping() {
        val keyframe = Keyframe(
            timestampMs = 1200L,
            posX = -0.3f,
            posY = 0.4f,
            scaleX = 1.8f,
            scaleY = 1.8f,
            rotation = 45f,
            opacity = 0.9f,
            interpolation = InterpolationType.EASE_IN_OUT
        )

        val dto = keyframe.toDto()
        val mappedBack = dto.toDomain()

        assertEquals(keyframe.id, mappedBack.id)
        assertEquals(keyframe.timestampMs, mappedBack.timestampMs)
        assertEquals(keyframe.posX, mappedBack.posX, 0.001f)
        assertEquals(keyframe.posY, mappedBack.posY, 0.001f)
        assertEquals(keyframe.scaleX, mappedBack.scaleX, 0.001f)
        assertEquals(keyframe.rotation, mappedBack.rotation, 0.001f)
        assertEquals(keyframe.opacity, mappedBack.opacity, 0.001f)
        assertEquals(InterpolationType.EASE_IN_OUT, mappedBack.interpolation)
    }

    @Test
    fun testAspectRatio_parsing() {
        assertEquals(AspectRatio.RATIO_16_9, AspectRatio.fromLabel("16:9"))
        assertEquals(AspectRatio.RATIO_9_16, AspectRatio.fromLabel("9:16"))
        assertEquals(AspectRatio.RATIO_1_1, AspectRatio.fromLabel("1:1"))
        assertEquals(AspectRatio.RATIO_4_5, AspectRatio.fromLabel("4:5"))
        assertEquals(AspectRatio.RATIO_16_9, AspectRatio.fromLabel("unknown"))
    }
}
