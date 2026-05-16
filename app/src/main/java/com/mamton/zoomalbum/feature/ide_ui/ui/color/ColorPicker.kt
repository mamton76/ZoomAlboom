package com.mamton.zoomalbum.feature.ide_ui.ui.color

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import kotlin.math.roundToInt

/**
 * Default preset swatches: black, white, grey, red, green, blue.
 */
val DEFAULT_PRESETS: List<Color> = listOf(
    Color(0xFF000000),
    Color(0xFFFFFFFF),
    Color(0xFF888888),
    Color(0xFFFF5555),
    Color(0xFF5FBF5F),
    Color(0xFF5F8FFF),
)

/**
 * Inline HSV/RGB color picker with preset swatches.
 *
 * Internal state is HSV+alpha (so dragging through grey doesn't lose hue).
 * Emits a Compose [Color] on every change via [onChange].
 */
@Composable
fun ColorPicker(
    initial: Color,
    presets: List<Color> = DEFAULT_PRESETS,
    onChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialHsv = remember(initial) { colorToHsv(initial) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember { mutableFloatStateOf(initial.alpha) }
    var hexText by remember { mutableStateOf(toHex(initial)) }
    var hexEditingError by remember { mutableStateOf(false) }

    val currentColor = remember(hue, saturation, value, alpha) {
        hsvToColor(hue, saturation, value, alpha)
    }

    LaunchedEffect(currentColor) {
        onChange(currentColor)
        if (!hexEditingError) hexText = toHex(currentColor)
    }

    Column(modifier = modifier) {
        // Preset swatches
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { p ->
                val hsv = remember(p) { colorToHsv(p) }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(p)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable {
                            hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                            alpha = p.alpha
                        },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Saturation/Value square (S = x, V = 1 - y)
        SaturationValueSquare(
            hue = hue,
            saturation = saturation,
            value = value,
            onChange = { s, v -> saturation = s; value = v },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Hue slider
        HueSlider(
            hue = hue,
            onChange = { hue = it },
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Alpha slider
        AlphaSlider(
            baseColor = hsvToColor(hue, saturation, value, 1f),
            alpha = alpha,
            onChange = { alpha = it },
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Hex input + current-color preview swatch
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(currentColor)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = hexText,
                onValueChange = { raw ->
                    hexText = raw
                    val parsed = parseHexInput(raw)
                    if (parsed != null) {
                        hexEditingError = false
                        val hsv = colorToHsv(parsed)
                        hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                        alpha = parsed.alpha
                    } else {
                        hexEditingError = true
                    }
                },
                isError = hexEditingError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                label = { Text("Hex") },
                modifier = Modifier.width(160.dp),
            )
        }
    }
}

@Composable
private fun SaturationValueSquare(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (s: Float, v: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val pureHue = remember(hue) { hsvToColor(hue, 1f, 1f, 1f) }

    fun update(o: Offset) {
        if (sizePx.width == 0 || sizePx.height == 0) return
        val s = (o.x / sizePx.width).coerceIn(0f, 1f)
        val v = 1f - (o.y / sizePx.height).coerceIn(0f, 1f)
        onChange(s, v)
    }

    Box(
        modifier = modifier
            .onSizeChanged { sizePx = it }
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.horizontalGradient(listOf(Color.White, pureHue)),
            )
            .drawBehind {
                // Vertical overlay: transparent → black
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                    size = Size(size.width, size.height),
                )
                // Marker ring at current S/V
                val mx = saturation * size.width
                val my = (1f - value) * size.height
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = Offset(mx, my),
                    style = Stroke(width = 2f),
                )
                drawCircle(
                    color = Color.Black,
                    radius = 9f,
                    center = Offset(mx, my),
                    style = Stroke(width = 1f),
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { update(it) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> update(change.position) }
            },
    )
}

@Composable
private fun HueSlider(
    hue: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    val hueColors = remember {
        // 0, 60, 120, 180, 240, 300, 360
        listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
            .map { hsvToColor(it, 1f, 1f, 1f) }
    }

    fun update(x: Float) {
        if (sizePx.width == 0) return
        val h = (x / sizePx.width).coerceIn(0f, 1f) * 360f
        onChange(h)
    }

    Box(
        modifier = modifier
            .onSizeChanged { sizePx = it }
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(hueColors))
            .drawBehind {
                val mx = (hue / 360f) * size.width
                drawRect(
                    color = Color.White,
                    topLeft = Offset(mx - 2f, 0f),
                    size = Size(4f, size.height),
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { update(it.x) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> update(change.position.x) }
            },
    )
}

@Composable
private fun AlphaSlider(
    baseColor: Color,
    alpha: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    fun update(x: Float) {
        if (sizePx.width == 0) return
        onChange((x / sizePx.width).coerceIn(0f, 1f))
    }

    Box(
        modifier = modifier
            .onSizeChanged { sizePx = it }
            .clip(RoundedCornerShape(12.dp))
            .background(checkerBrush())
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(baseColor.copy(alpha = 0f), baseColor.copy(alpha = 1f)),
                    ),
                    size = Size(size.width, size.height),
                )
                val mx = alpha * size.width
                drawRect(
                    color = Color.White,
                    topLeft = Offset(mx - 2f, 0f),
                    size = Size(4f, size.height),
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { update(it.x) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> update(change.position.x) }
            },
    )
}

// Simple checker brush for alpha slider background.
private fun checkerBrush(): Brush = SolidColor(Color(0xFFBBBBBB))

// ── Color math ──────────────────────────────────────────────────────────

private fun hsvToColor(hue: Float, saturation: Float, value: Float, alpha: Float): Color {
    val argb = android.graphics.Color.HSVToColor(
        (alpha * 255).roundToInt().coerceIn(0, 255),
        floatArrayOf(hue, saturation, value),
    )
    return Color(argb)
}

private fun colorToHsv(color: Color): FloatArray {
    val argb = color.toArgb()
    val out = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF,
        out,
    )
    return out
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt().coerceIn(0, 255),
    (red * 255).roundToInt().coerceIn(0, 255),
    (green * 255).roundToInt().coerceIn(0, 255),
    (blue * 255).roundToInt().coerceIn(0, 255),
)

internal fun toHex(color: Color): String {
    val a = (color.alpha * 255).roundToInt().coerceIn(0, 255)
    val r = (color.red * 255).roundToInt().coerceIn(0, 255)
    val g = (color.green * 255).roundToInt().coerceIn(0, 255)
    val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
    return if (a == 255) "#%02X%02X%02X".format(r, g, b)
    else "#%02X%02X%02X%02X".format(a, r, g, b)
}

internal fun parseHexInput(raw: String): Color? {
    val s = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
    if (s.length != 7 && s.length != 9) return null
    return runCatching { Color(s.toColorInt()) }.getOrNull()
}
