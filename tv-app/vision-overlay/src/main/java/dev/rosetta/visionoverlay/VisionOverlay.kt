package dev.rosetta.visionoverlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * One object a vision model found, in normalized 0..1 image coordinates (left/top/right/bottom).
 * Use [fromXyxy1000] or [fromYxyx1000] to convert a model's raw box.
 */
data class Detection(
    val id: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Resolved detections animate out (e.g. an item that was put away, a question that was answered). */
    val resolved: Boolean = false,
    /** Optional short label drawn by the caller in the badge slot. */
    val label: String = "",
) {
    val width get() = right - left
    val height get() = bottom - top

    companion object {
        /**
         * From `[x1, y1, x2, y2]` on a 0–1000 grid (Amazon Nova grounding, many OCR and detection APIs).
         * Corners may come in any order and values outside the image are clamped.
         */
        fun fromXyxy1000(id: String, box: List<Number>, resolved: Boolean = false, label: String = ""): Detection {
            require(box.size == 4) { "box must have 4 numbers, got ${box.size}" }
            val (x1, y1, x2, y2) = box.map { it.toFloat() }
            return normalized(id, x1, y1, x2, y2, 1000f, resolved, label)
        }

        /** From `[ymin, xmin, ymax, xmax]` on a 0–1000 grid (Google Gemini's detection convention). */
        fun fromYxyx1000(id: String, box: List<Number>, resolved: Boolean = false, label: String = ""): Detection {
            require(box.size == 4) { "box must have 4 numbers, got ${box.size}" }
            val (y1, x1, y2, x2) = box.map { it.toFloat() }
            return normalized(id, x1, y1, x2, y2, 1000f, resolved, label)
        }

        /** From pixel coordinates `[x1, y1, x2, y2]` of an image of the given size. */
        fun fromPixels(id: String, box: List<Number>, imageWidth: Int, imageHeight: Int, resolved: Boolean = false, label: String = ""): Detection {
            require(box.size == 4) { "box must have 4 numbers, got ${box.size}" }
            require(imageWidth > 0 && imageHeight > 0) { "image size must be positive" }
            val (x1, y1, x2, y2) = box.map { it.toFloat() }
            return normalized(id, x1 / imageWidth, y1 / imageHeight, x2 / imageWidth, y2 / imageHeight, 1f, resolved, label)
        }

        private fun normalized(id: String, x1: Float, y1: Float, x2: Float, y2: Float, scale: Float, resolved: Boolean, label: String): Detection {
            fun c(v: Float) = (v / scale).coerceIn(0f, 1f)
            return Detection(id, c(minOf(x1, x2)), c(minOf(y1, y2)), c(maxOf(x1, x2)), c(maxOf(y1, y2)), resolved, label)
        }
    }
}

/** Where the optional badge sits: above the box (objects in a photo) or after it (lines in a document, like a tick). */
enum class BadgePlacement { Above, End }

/** Visual tuning for [VisionOverlay]. Defaults read well on a TV at 3 m. */
data class VisionOverlayStyle(
    val outline: Color = Color(0xFFF4C56A),
    val fill: Color = Color(0x14F4C56A),
    val resolvedOutline: Color = Color(0xFF9BE37A),
    val strokeWidth: Dp = 2.dp,
    val corner: Dp = 12.dp,
    val padding: Dp = 4.dp,
    val badgeSize: Dp = 54.dp,
    val staggerMillis: Long = 220,
    /** Smallest box drawn, so tiny detections (a single digit, a button) stay visible from the sofa. */
    val minBoxSize: Dp = 18.dp,
    val badgePlacement: BadgePlacement = BadgePlacement.Above,
)

/**
 * Draws AI detection boxes over an image on Android TV / Fire TV: boxes pop in one by one, unresolved ones
 * can pulse for attention, and an optional circular [badge] (e.g. an icon or character) perches above each box.
 * It is purely visual and never takes D-pad focus.
 *
 * Place it in a Box with exactly the same bounds as the image it annotates:
 * ```
 * Box(Modifier.size(w, h)) {
 *     Image(photo, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
 *     VisionOverlay(detections, Modifier.fillMaxSize(), pulseUnresolved = true) { d -> Icon(...) }
 * }
 * ```
 */
@Composable
fun VisionOverlay(
    detections: List<Detection>,
    modifier: Modifier = Modifier,
    pulseUnresolved: Boolean = false,
    style: VisionOverlayStyle = VisionOverlayStyle(),
    badge: (@Composable (Detection) -> Unit)? = null,
) {
    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        detections.forEachIndexed { index, d ->
            key(d.id) { DetectionMarker(d, index, w, h, pulseUnresolved, style, badge) }
        }
    }
}

/** Where a detection is drawn inside a [width] x [height] overlay, after padding and the minimum size. */
internal data class MarkerBounds(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

internal fun markerBounds(d: Detection, width: Dp, height: Dp, style: VisionOverlayStyle): MarkerBounds {
    val bw = maxOf(width * d.width + style.padding * 2, style.minBoxSize)
    val bh = maxOf(height * d.height + style.padding * 2, style.minBoxSize)
    val cx = width * (d.left + d.right) / 2
    val cy = height * (d.top + d.bottom) / 2
    return MarkerBounds(cx - bw / 2, cy - bh / 2, bw, bh)
}

@Composable
private fun DetectionMarker(
    d: Detection,
    index: Int,
    w: Dp,
    h: Dp,
    pulseUnresolved: Boolean,
    style: VisionOverlayStyle,
    badge: (@Composable (Detection) -> Unit)?,
) {
    val presence = remember { Animatable(0f) }
    LaunchedEffect(d.resolved) {
        if (d.resolved) {
            presence.animateTo(0f, tween(450))
        } else {
            delay(index * style.staggerMillis)
            presence.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (pulseUnresolved && !d.resolved) 1.1f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale",
    )
    val b = markerBounds(d, w, h, style)
    val shape = RoundedCornerShape(style.corner)
    Box(
        Modifier
            .offset(b.x, b.y)
            .size(b.width, b.height)
            .graphicsLayer { alpha = presence.value; scaleX = pulse; scaleY = pulse }
            .border(style.strokeWidth, if (d.resolved) style.resolvedOutline else style.outline, shape)
            .background(style.fill, shape),
    )
    if (badge != null) {
        val s = style.badgeSize
        val (bx, by) = when (style.badgePlacement) {
            BadgePlacement.Above -> (b.x + b.width / 2 - s / 2) to (b.y - s - 6.dp).let { if (it < 4.dp) 4.dp else it }
            BadgePlacement.End -> (b.x + b.width + 8.dp) to (b.y + b.height / 2 - s / 2)
        }
        Box(
            Modifier
                .offset(bx, by)
                .size(s)
                .graphicsLayer {
                    val p = presence.value * pulse
                    scaleX = p; scaleY = p
                    // Resolved badges float upward as they leave ("going home").
                    translationY = (1f - presence.value) * -40f * density
                    alpha = presence.value
                }
                .clip(CircleShape)
                .border(2.dp, style.outline, CircleShape),
        ) { badge(d) }
    }
}
