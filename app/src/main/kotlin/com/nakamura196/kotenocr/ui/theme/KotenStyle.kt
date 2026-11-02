package com.nakamura196.kotenocr.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Mincho / Serif text style helper (`Font.mincho` equivalent).
 */
fun minchoStyle(
    size: TextUnit,
    bold: Boolean = false,
    color: Color = Color.Unspecified
): TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = size,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
    color = color
)

/**
 * Warm Washi paper background with subtle radial fiber gradients (`WashiBackground` in `KotenStyle.swift`).
 */
@Composable
fun WashiBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val washi = KotenColor.washi
    val card = KotenColor.washiCard
    val mount = KotenColor.mount

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(washi)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxDim = size.maxDimension.coerceAtLeast(1f)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(card.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.08f),
                    radius = maxDim * 0.7f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(mount.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width * 0.90f, size.height * 0.92f),
                    radius = maxDim * 0.75f
                )
            )
        }
        content()
    }
}

/**
 * Manuscript mounting frame (`kotenMount` modifier in `KotenStyle.swift`).
 */
@Composable
fun Modifier.kotenMount(padding: Dp = 8.dp): Modifier {
    val mount = KotenColor.mount
    val rule = KotenColor.rule
    return this
        .padding(padding)
        .shadow(elevation = 6.dp, shape = RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.15f))
        .background(mount, RoundedCornerShape(10.dp))
        .border(1.dp, rule, RoundedCornerShape(10.dp))
        .padding(padding)
}

/**
 * Outlined vermilion seal ("典") for headers (`KotenSeal` in `KotenStyle.swift`).
 */
@Composable
fun KotenSeal(
    character: String = "典",
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    val shu = KotenColor.shu
    val corner = size * 0.18f
    Box(
        modifier = modifier
            .size(size)
            .border(1.8.dp, shu, RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = character,
            style = minchoStyle(
                size = (size.value * 0.56f).sp,
                bold = true,
                color = shu
            )
        )
    }
}

/**
 * Filled vermilion hanko stamp ("典") with double-frame border and L-shaped scan corners
 * inside (`KotenStamp` in `KotenStyle.swift`).
 */
@Composable
fun KotenStamp(
    character: String = "典",
    size: Dp = 76.dp,
    modifier: Modifier = Modifier
) {
    val shuFill = KotenColor.shuFill
    val cream = Color(0.99f, 0.96f, 0.90f)
    val radius = size * 0.17f
    val inset = size * 0.066f
    val innerRadius = size * 0.12f

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(radius), spotColor = Color.Black.copy(alpha = 0.25f))
            .background(shuFill, RoundedCornerShape(radius))
            .border(1.5.dp, cream.copy(alpha = 0.28f), RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
                .border(1.2.dp, cream.copy(alpha = 0.36f), RoundedCornerShape(innerRadius))
        )
        ScanCorners(
            color = cream.copy(alpha = 0.92f),
            arm = size * 0.17f,
            lineWidth = (size * 0.036f).coerceAtLeast(2.dp),
            cornerRadius = size * 0.04f,
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.13f)
        )
        Text(
            text = character,
            style = minchoStyle(
                size = (size.value * 0.52f).sp,
                bold = true,
                color = cream
            )
        )
    }
}

/**
 * Four L-shaped corner brackets (`ScanCorners` in `KotenStyle.swift`).
 */
@Composable
fun ScanCorners(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE0684C),
    arm: Dp = 28.dp,
    lineWidth: Dp = 3.dp,
    cornerRadius: Dp = 6.dp
) {
    Canvas(modifier = modifier) {
        val a = arm.toPx()
        val r = minOf(cornerRadius.toPx(), a * 0.5f)
        val stroke = Stroke(
            width = lineWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val w = size.width
        val h = size.height

        val path = Path().apply {
            // Top-left
            moveTo(0f, a)
            lineTo(0f, r)
            arcTo(Rect(0f, 0f, 2f * r, 2f * r), 180f, 90f, false)
            lineTo(a, 0f)

            // Top-right
            moveTo(w - a, 0f)
            lineTo(w - r, 0f)
            arcTo(Rect(w - 2f * r, 0f, w, 2f * r), 270f, 90f, false)
            lineTo(w, a)

            // Bottom-left
            moveTo(0f, h - a)
            lineTo(0f, h - r)
            arcTo(Rect(0f, h - 2f * r, 2f * r, h), 180f, -90f, false)
            lineTo(a, h)

            // Bottom-right
            moveTo(w - a, h)
            lineTo(w - r, h)
            arcTo(Rect(w - 2f * r, h - 2f * r, w, h), 90f, -90f, false)
            lineTo(w, h - a)
        }
        drawPath(path = path, color = color, style = stroke)
    }
}

/**
 * Primary vermilion button (`KotenProminentButtonStyle` in `KotenStyle.swift`).
 */
@Composable
fun KotenProminentButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val shuFill = KotenColor.shuFill
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = shuFill,
            contentColor = Color.White,
            disabledContainerColor = shuFill.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        ),
        contentPadding = contentPadding,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
        content = content
    )
}

/**
 * Secondary Washi card / glass button (`KotenGlassButtonStyle` in `KotenStyle.swift`).
 */
@Composable
fun KotenGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isCircle: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val card = KotenColor.washiCard
    val ink = KotenColor.ink
    val rule = KotenColor.rule
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = if (isCircle) CircleShape else RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = card.copy(alpha = 0.92f),
            contentColor = ink,
            disabledContainerColor = card.copy(alpha = 0.45f),
            disabledContentColor = ink.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, rule.copy(alpha = 0.75f)),
        contentPadding = contentPadding,
        // No elevation: with a translucent container the shadow shows through as an inner box.
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        content = content
    )
}

/**
 * One-time coach bubble (`CoachBubble` + `BubbleShape` in `KotenStyle.swift`):
 * washi card, 朱 3dp outline, Mincho 18 bold, with a pointer drawn into the top or bottom edge.
 * [pointerX] is the pointer's horizontal position, 0..1. When [onDismiss] is null the bubble
 * does not take touches (iOS `.allowsHitTesting(false)`).
 */
@Composable
fun CoachBubble(
    text: String,
    modifier: Modifier = Modifier,
    pointerUp: Boolean = false,
    pointerX: Float = 0.5f,
    onDismiss: (() -> Unit)? = null
) {
    val ink = KotenColor.ink
    val card = KotenColor.washiCard
    val shu = KotenColor.shu
    val tapModifier = if (onDismiss != null) Modifier.clickable { onDismiss() } else Modifier
    Box(
        modifier = modifier
            .then(tapModifier)
            .padding(top = if (pointerUp) 12.dp else 0.dp, bottom = if (pointerUp) 0.dp else 12.dp)
    ) {
        Text(
            text = text,
            style = minchoStyle(size = 18.sp, bold = true, color = ink),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .drawBehind {
                    val path = bubblePath(size, pointerUp, pointerX, 16.dp.toPx(), 12.dp.toPx())
                    translate(top = 6.dp.toPx()) {
                        drawPath(path, Color.Black.copy(alpha = 0.12f))
                    }
                    drawPath(path, card)
                    drawPath(path, shu, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
                }
                .padding(horizontal = 18.dp, vertical = 12.dp)
        )
    }
}

private fun bubblePath(size: Size, pointerUp: Boolean, pointerX: Float, radius: Float, tip: Float): Path {
    val w = size.width
    val h = size.height
    val x = (w * pointerX).coerceIn(radius + tip, max(radius + tip, w - radius - tip))
    return Path().apply {
        moveTo(radius, 0f)
        if (pointerUp) {
            lineTo(x - tip, 0f); lineTo(x, -tip); lineTo(x + tip, 0f)
        }
        lineTo(w - radius, 0f)
        quadraticTo(w, 0f, w, radius)
        lineTo(w, h - radius)
        quadraticTo(w, h, w - radius, h)
        if (!pointerUp) {
            lineTo(x + tip, h); lineTo(x, h + tip); lineTo(x - tip, h)
        }
        lineTo(radius, h)
        quadraticTo(0f, h, 0f, h - radius)
        lineTo(0f, radius)
        quadraticTo(0f, 0f, radius, 0f)
        close()
    }
}
