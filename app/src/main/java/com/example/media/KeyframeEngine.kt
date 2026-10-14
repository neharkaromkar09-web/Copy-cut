package com.example.media

import com.example.data.model.InterpolationType
import com.example.data.model.Keyframe
import kotlin.math.pow

data class EvaluatedTransform(
    val posX: Float = 0f,
    val posY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f
)

object KeyframeEngine {

    /**
     * Evaluates the transform at the given clip offset time in milliseconds.
     */
    fun evaluate(keyframes: List<Keyframe>, clipOffsetMs: Long): EvaluatedTransform {
        if (keyframes.isEmpty()) {
            return EvaluatedTransform()
        }
        val sorted = keyframes.sortedBy { it.timestampMs }
        if (clipOffsetMs <= sorted.first().timestampMs) {
            val k = sorted.first()
            return EvaluatedTransform(
                posX = k.posX,
                posY = k.posY,
                scaleX = k.scaleX,
                scaleY = k.scaleY,
                rotation = k.rotation,
                opacity = k.opacity
            )
        }
        if (clipOffsetMs >= sorted.last().timestampMs) {
            val k = sorted.last()
            return EvaluatedTransform(
                posX = k.posX,
                posY = k.posY,
                scaleX = k.scaleX,
                scaleY = k.scaleY,
                rotation = k.rotation,
                opacity = k.opacity
            )
        }

        // Find surrounding keyframes
        var prev = sorted.first()
        var next = sorted.last()
        for (i in 0 until sorted.size - 1) {
            if (clipOffsetMs >= sorted[i].timestampMs && clipOffsetMs <= sorted[i + 1].timestampMs) {
                prev = sorted[i]
                next = sorted[i + 1]
                break
            }
        }

        val span = (next.timestampMs - prev.timestampMs).coerceAtLeast(1L)
        val linearProgress = ((clipOffsetMs - prev.timestampMs).toFloat() / span).coerceIn(0f, 1f)
        val progress = applyInterpolation(linearProgress, prev.interpolation)

        return EvaluatedTransform(
            posX = lerp(prev.posX, next.posX, progress),
            posY = lerp(prev.posY, next.posY, progress),
            scaleX = lerp(prev.scaleX, next.scaleX, progress),
            scaleY = lerp(prev.scaleY, next.scaleY, progress),
            rotation = lerp(prev.rotation, next.rotation, progress),
            opacity = lerp(prev.opacity, next.opacity, progress).coerceIn(0f, 1f)
        )
    }

    fun applyInterpolation(t: Float, type: InterpolationType): Float {
        return when (type) {
            InterpolationType.LINEAR -> t
            InterpolationType.EASE_IN -> t.pow(2f)
            InterpolationType.EASE_OUT -> 1f - (1f - t).pow(2f)
            InterpolationType.EASE_IN_OUT -> {
                if (t < 0.5f) {
                    2f * t.pow(2f)
                } else {
                    1f - (-2f * t + 2f).pow(2f) / 2f
                }
            }
        }
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
}
