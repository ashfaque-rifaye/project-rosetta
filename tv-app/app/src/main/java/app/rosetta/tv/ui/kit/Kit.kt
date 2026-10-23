package app.rosetta.tv.ui.kit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.rosetta.tv.ui.common.AssetImage
import app.rosetta.tv.ui.theme.Rosetta

/* ---------------------------------------------------------------------------------------------
 * Rosetta UI kit. Game-lobby energy (casino/collectible references) on a night-museum palette.
 * Everything focusable lifts, glows and plays a soft tick so the remote always feels responsive.
 * ------------------------------------------------------------------------------------------- */

enum class ButtonTone { Gold, Lime, Ember, Glass }

/** A game button's colours: the face gradient (top to bottom), the darker lip under it, its outline, and text. */
private class Skin(val face: List<Color>, val lip: Color, val rim: Color, val text: Color, val glow: Color)

private fun skin(tone: ButtonTone) = when (tone) {
    ButtonTone.Gold -> Skin(listOf(Color(0xFFFFF4C2), Color(0xFFFFD45A), Color(0xFFF2A12C)), Color(0xFFA85B12), Color(0xFF4A2504), Color(0xFF3A1D02), Rosetta.Gold)
    ButtonTone.Lime -> Skin(listOf(Color(0xFFF2FFB8), Color(0xFFC6F432), Color(0xFF7FB914)), Color(0xFF4A7008), Color(0xFF223604), Color(0xFF1C2C02), Color(0xFFC6F432))
    ButtonTone.Ember -> Skin(listOf(Color(0xFFFFD2A8), Color(0xFFFF8F4F), Color(0xFFE2522A)), Color(0xFF94300F), Color(0xFF3E1205), Color(0xFF2E0B02), Rosetta.Ember)
    ButtonTone.Glass -> Skin(listOf(Color(0xFF6A7FE0), Color(0xFF3B4DA8), Color(0xFF26357A)), Color(0xFF141C48), Color(0xFF070B1E), Color.White, Color(0xFF8FA6FF))
}

private val TextOutline = Shadow(Color(0xAA000000), Offset(0f, 2.5f), 3f)

/**
 * The game button: a chunky raised tile with a darker lip underneath (it presses down into it), a bevelled face with a
 * bright top band, a dark rim, a gem-like icon badge and bold outlined text. Focused, it lifts, glows and a sheen sweeps
 * across it. [sub] adds a small second line (e.g. "+12 XP").
 */
@Composable
fun RosettaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    sub: String? = null,
    tone: ButtonTone = ButtonTone.Gold,
    enabled: Boolean = true,
    big: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (focused && enabled) 1f else 0f, tween(200), label = "glow")
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        -0.4f, 1.4f, infiniteRepeatable(tween(1900), RepeatMode.Restart), label = "x",
    )
    val sk = skin(tone)
    val corner = if (big) 22.dp else 18.dp
    val lip = if (big) 6.dp else 5.dp
    val shape = RoundedCornerShape(corner)
    Surface(
        onClick = { if (enabled) { Sfx.play(Sfx.Select); onClick() } },
        modifier = modifier
            .onFocusChanged { if (it.isFocused && !focused) Sfx.play(Sfx.Move); focused = it.isFocused }
            .drawBehind {
                if (glow > 0f) {
                    val spread = 20.dp.toPx() * glow
                    drawRoundRect(
                        brush = Brush.radialGradient(listOf(sk.glow.copy(alpha = 0.6f * glow), Color.Transparent), center = center, radius = size.maxDimension * 0.8f),
                        topLeft = Offset(-spread, -spread), size = Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = CornerRadius(corner.toPx() + spread),
                    )
                }
            }
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f; translationY = -3.dp.toPx() * glow },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent, contentColor = sk.text, focusedContentColor = sk.text,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f, pressedScale = 0.95f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = shape),
            focusedBorder = Border(BorderStroke(0.dp, Color.Transparent), shape = shape),
        ),
    ) {
        Box(
            Modifier.drawBehind {
                val r = CornerRadius(corner.toPx())
                val lipPx = lip.toPx()
                val faceH = size.height - lipPx
                // The lip (depth), then the bevelled face on top, then the rim.
                drawRoundRect(sk.lip, cornerRadius = r)
                drawRoundRect(Brush.verticalGradient(sk.face, endY = faceH), size = Size(size.width, faceH), cornerRadius = r)
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.0f)), startY = 0f, endY = faceH * 0.5f),
                    topLeft = Offset(3.dp.toPx(), 2.dp.toPx()), size = Size(size.width - 6.dp.toPx(), faceH * 0.46f), cornerRadius = CornerRadius(corner.toPx() * 0.8f),
                )
                drawRoundRect(Color.Black.copy(alpha = 0.18f), topLeft = Offset(0f, faceH - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx()), cornerRadius = r)
                drawRoundRect(if (glow > 0f) Color.White.copy(alpha = 0.95f * glow + 0.05f) else sk.rim, cornerRadius = r, style = Stroke(if (glow > 0f) 3.dp.toPx() else 2.dp.toPx()))
                if (focused) {
                    val x = size.width * shimmer
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent), start = Offset(x - 90f, 0f), end = Offset(x + 30f, faceH)))
                }
            },
        ) {
            Row(
                Modifier.padding(start = if (icon != null) 8.dp else 26.dp, end = 24.dp, top = if (big) 10.dp else 7.dp, bottom = (if (big) 10.dp else 7.dp) + lip),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Box(
                        Modifier.size(if (big) 46.dp else 38.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), sk.lip.copy(alpha = 0.55f)), radius = 60f))
                            .border(2.dp, sk.rim.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(icon, fontSize = if (big) 23.sp else 19.sp) }
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(
                        label, maxLines = 1, color = sk.text,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black, fontSize = if (big) 22.sp else 18.sp, letterSpacing = 0.3.sp,
                            shadow = if (sk.text == Color.White) TextOutline else Shadow(Color.White.copy(alpha = 0.55f), Offset(0f, 1.5f), 0f),
                        ),
                    )
                    if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = sk.text.copy(alpha = 0.78f), maxLines = 1)
                }
            }
        }
    }
}

/** A game panel: deep gradient, a bright bevel along the top, and a double frame (dark outer, light inner). */
@Composable
fun Glass(modifier: Modifier = Modifier, radius: Dp = 22.dp, glow: Color? = null, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .then(if (glow != null) Modifier.shadow(26.dp, shape, ambientColor = glow, spotColor = glow) else Modifier)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xF2223064), Color(0xF2121B3E), Color(0xF20C1330))))
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), endY = 46.dp.toPx()))
                val r = CornerRadius(radius.toPx())
                drawRoundRect(Color(0xFF050914), cornerRadius = r, style = Stroke(3.dp.toPx()))
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.05f))),
                    topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
                    cornerRadius = CornerRadius((radius - 3.dp).toPx()), style = Stroke(1.2.dp.toPx()),
                )
            },
        content = content,
    )
}

/** Focusable game card: framed and bevelled like [Glass]; focused, it lifts with an accent frame and glow. */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    accent: Color = Rosetta.Gold,
    onFocus: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(radius)
    Surface(
        onClick = { Sfx.play(Sfx.Select); onClick() },
        modifier = modifier.onFocusChanged {
            if (it.isFocused && !focused) { Sfx.play(Sfx.Move); onFocus() }
            focused = it.isFocused
        },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = androidx.tv.material3.ClickableSurfaceDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(elevationColor = accent.copy(alpha = 0.7f), elevation = 20.dp),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(2.dp, Color(0xFF050914)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, accent), shape = shape),
        ),
    ) {
        Box(
            Modifier.background(Brush.verticalGradient(listOf(Color(0xF5243368), Color(0xF5141D44), Color(0xF50C1330))))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (focused) 0.16f else 0.09f), Color.Transparent), endY = size.height * 0.45f))
                    drawRoundRect(
                        Color.White.copy(alpha = if (focused) 0.0f else 0.14f),
                        topLeft = Offset(2.dp.toPx(), 2.dp.toPx()), size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
                        cornerRadius = CornerRadius((radius - 2.dp).toPx()), style = Stroke(1.dp.toPx()),
                    )
                },
            content = content,
        )
    }
}

/** Currency / stat chip: coins, XP, streak, glyphs. */
@Composable
fun StatChip(icon: String, value: String, modifier: Modifier = Modifier, tint: Color = Rosetta.Gold) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC0B1226))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Rosetta.Cream)
    }
}

/** Segmented "jackpot" meter with glowing fill (family goal). */
@Composable
fun GoalMeter(progress: Float, modifier: Modifier = Modifier, segments: Int = 9, fill: Color = Rosetta.Gold) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow), label = "p")
    val pulse by rememberInfiniteTransition(label = "m").animateFloat(0.6f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "a")
    Canvas(modifier.height(18.dp)) {
        val gap = 5.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        repeat(segments) { i ->
            val x = i * (w + gap)
            val filled = ((p * segments) - i).coerceIn(0f, 1f)
            drawRoundRect(Color(0x33FFFFFF), Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2))
            if (filled > 0f) {
                val last = filled < 1f || i == (p * segments).toInt() - 1
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFFFFE9A8), fill, Color(0xFFD98E2E))),
                    Offset(x, 0f), Size(w * filled, size.height), CornerRadius(size.height / 2),
                    alpha = if (last) pulse else 1f,
                )
            }
        }
    }
}

/** Circular XP ring around an avatar. */
@Composable
fun AvatarRing(asset: String, progress: Float, size: Dp, modifier: Modifier = Modifier, ring: Color = Color(0xFFC6F432), level: Int? = null) {
    val p by animateFloatAsState(progress, tween(900), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = 4.dp.toPx()
            drawArc(Color(0x33FFFFFF), 0f, 360f, false, style = Stroke(s), topLeft = Offset(s / 2, s / 2), size = Size(this.size.width - s, this.size.height - s))
            drawArc(ring, -90f, 360f * p, false, style = Stroke(s, cap = StrokeCap.Round), topLeft = Offset(s / 2, s / 2), size = Size(this.size.width - s, this.size.height - s))
        }
        AssetImage(asset, Modifier.size(size - 12.dp).clip(CircleShape))
        if (level != null) {
            Text(
                "$level",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                color = Rosetta.Night,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(ring).padding(top = 4.dp),
            )
        }
    }
}

enum class Rarity(val label: String, val color: Color, val glow: Color) {
    Common("COMMON", Color(0xFFA9B4CC), Color(0x66A9B4CC)),
    Rare("RARE", Color(0xFF6BC6FF), Color(0x996BC6FF)),
    Epic("EPIC", Color(0xFFC77DFF), Color(0x99C77DFF)),
    Legendary("LEGENDARY", Color(0xFFF4C56A), Color(0xCCF4C56A)),
}

/** Collectible card frame with rarity colour, holo sheen and a ribbon. */
@Composable
fun RarityFrame(rarity: Rarity, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val sheen by rememberInfiniteTransition(label = "holo").animateFloat(0f, 1f, infiniteRepeatable(tween(3200), RepeatMode.Restart), label = "s")
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(rarity.color.copy(alpha = 0.35f), Color(0xFF0B1226))))
            .border(2.dp, Brush.linearGradient(listOf(rarity.color, Color.White.copy(alpha = 0.7f), rarity.color)), shape)
            .drawBehind {
                if (rarity >= Rarity.Rare) {
                    val x = size.width * (sheen * 2f - 0.5f)
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), rarity.color.copy(alpha = 0.18f), Color.Transparent),
                            start = Offset(x - 120f, 0f), end = Offset(x + 120f, size.height),
                        ),
                    )
                }
            },
        content = content,
    )
}

@Composable
fun RarityRibbon(rarity: Rarity, modifier: Modifier = Modifier) {
    Text(
        rarity.label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.6.sp),
        color = Rosetta.Night,
        modifier = modifier.clip(RoundedCornerShape(50)).background(rarity.color).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Section heading with a thin gold rule, used above every rail. */
@Composable
fun RailHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = Rosetta.Gold)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Rosetta.Gold.copy(alpha = 0.5f), Color.Transparent))))
        if (trailing != null) {
            Spacer(Modifier.width(14.dp))
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = Rosetta.Mist)
        }
    }
}

/** Small rounded pill label. */
@Composable
fun Pill(text: String, color: Color = Rosetta.Gold, filled: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = if (filled) Rosetta.Night else color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun RowScope.Fill() = Spacer(Modifier.weight(1f))

@Composable
fun HStack(gap: Dp = 12.dp, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically, content = content)
