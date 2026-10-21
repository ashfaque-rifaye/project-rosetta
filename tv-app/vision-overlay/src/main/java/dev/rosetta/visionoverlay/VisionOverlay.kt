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
 * One object a vision model found, in normalized 0..1 image coordinates.
 * Models that use 0–1000 grounding (e.g. Amazon Nova) divide by 1000.
 */
data class Detection(
    val id: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Resolved detections animate out (e.g. an item that was put away). */
    val resolved: Boolean = false,
)

/** Visual tuning for [VisionOverlay]. Defaults read well on a TV at 3 m. */
data class VisionOverlayStyle(
    val outline: Color = Color(0xFFF4C56A),
    val fill: Color = Color(0x14F4C56A),
    val strokeWidth: Dp = 2.dp,
    val corner: Dp = 12.dp,
    val padding: Dp = 4.dp,
    val badgeSize: Dp = 54.dp,
    val staggerMillis: Long = 220,
)

/**
 * Draws AI detection boxes over an image on Android TV / Fire TV: boxes pop in one by one, unresolved ones
 * can pulse for attention, and an optional circular [badge] (e.g. an icon or character) perches above each box.
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
    val x = w * d.left
    val y = h * d.top
    val bw = w * (d.right - d.left)
    val bh = h * (d.bottom - d.top)
    val shape = RoundedCornerShape(style.corner)
    Box(
        Modifier
            .offset(x - style.padding, y - style.padding)
            .size(bw + style.padding * 2, bh + style.padding * 2)
            .graphicsLayer { alpha = presence.value; scaleX = pulse; scaleY = pulse }
            .border(style.strokeWidth, style.outline, shape)
            .background(style.fill, shape),
    )
    if (badge != null) {
        val s = style.badgeSize
        val by = (y - s - 6.dp).let { if (it < 4.dp) 4.dp else it }
        Box(
            Modifier
                .offset(x + bw / 2 - s / 2, by)
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
