package com.sudhirshahu.loopalarm.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.roundToInt

/** Crop shapes offered above the image. [ratio] is width / height in pixels; null means free. */
private enum class CropShape(val label: String, val ratio: Float?) {
    FREE("Free", null), SQUARE("Square", 1f), WIDE("Wide 2:1", 2f)
}

private enum class Tool { CROP, DRAW }

private const val MIN_SIDE = 0.08f

/** Pen colours and widths (as a fraction of the image width) for marking up a picture. */
private val PenColours = listOf(Color(0xFFE53935), Color(0xFFFFCA28), Color(0xFF43A047), Color(0xFF1E88E5), Color.White, Color.Black)
private val PenSizes = listOf(0.006f, 0.012f, 0.022f)

/** One pencil stroke; points and width are fractions of the image, so it lands in the same place at any size. */
class Mark(val points: List<Offset>, val colour: Color, val width: Float)

/** Smooth path through [points] (already in pixels): straight to the first midpoint, then curves through each point. */
private fun smoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points[0].x, points[0].y)
    if (points.size == 1) {
        lineTo(points[0].x + 0.01f, points[0].y)
        return@apply
    }
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val mid = Offset((prev.x + points[i].x) / 2, (prev.y + points[i].y) / 2)
        quadraticTo(prev.x, prev.y, mid.x, mid.y)
    }
    lineTo(points.last().x, points.last().y)
}

/** A copy of [source] with [marks] drawn on it at full resolution. Call off the main thread. */
fun applyMarks(source: Bitmap, marks: List<Mark>): Bitmap {
    if (marks.isEmpty()) return source
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(out)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    marks.forEach { m ->
        paint.color = m.colour.toArgb()
        paint.strokeWidth = m.width * out.width
        val pts = m.points.map { Offset(it.x * out.width, it.y * out.height) }
        canvas.drawPath(smoothPath(pts).asAndroidPath(), paint)
    }
    return out
}


/**
 * Full-screen picture editor with two tools.
 * Crop: drag inside the box to move it, drag a corner or a side to resize; the box follows the finger exactly.
 * Draw: mark things with a pencil in a few colours and sizes, with Undo and Clear.
 * [onDone] gets the crop as fractions of the image (left, top, right, bottom) and the pencil marks;
 * apply them with [applyMarks] before cropping. [startDrawing] and [initialMarks] set the starting state (screenshots).
 */
@Composable
fun CropDialog(
    source: Bitmap,
    onCancel: () -> Unit,
    onDone: (FloatArray, List<Mark>) -> Unit,
    startDrawing: Boolean = false,
    initialMarks: List<Mark> = emptyList(),
) {
    // Show a screen-sized copy: scaling a large photo on every frame is what made dragging stutter.
    val preview = remember(source) {
        val s = 1600f / max(source.width, source.height)
        (if (s < 1f) Bitmap.createScaledBitmap(source, (source.width * s).roundToInt(), (source.height * s).roundToInt(), true) else source)
            .asImageBitmap()
    }
    var tool by remember { mutableStateOf(if (startDrawing) Tool.DRAW else Tool.CROP) }
    var shape by remember { mutableStateOf(CropShape.FREE) }
    var crop by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    val marks = remember { mutableStateListOf<Mark>().apply { addAll(initialMarks) } }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var penColour by remember { mutableStateOf(PenColours[0]) }
    var penSize by remember { mutableStateOf(PenSizes[1]) }
    // Height of a crop fraction per unit of width fraction for a given pixel ratio.
    fun k(ratio: Float) = source.width / (source.height * ratio)

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
                Text("Edit picture", color = Color.White, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Button(onClick = { onDone(floatArrayOf(crop.left, crop.top, crop.right, crop.bottom), marks.toList()) }) { Text("Use") }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val scale = minOf(boxW / source.width, boxH / source.height)
                val imgW = source.width * scale
                val imgH = source.height * scale
                val handlePx = with(density) { 40.dp.toPx() }
                val currentShape by rememberUpdatedState(shape)
                val currentTool by rememberUpdatedState(tool)

                Box(Modifier.size(with(density) { imgW.toDp() }, with(density) { imgH.toDp() }).clipToBounds()) {
                    Image(preview, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(imgW, imgH) {
                            // Crop gesture: where it started and what it grabbed, so the box tracks the finger
                            // (no drift from clamping each small step).
                            var grab = Grab.NONE
                            var startCrop = crop
                            var total = Offset.Zero
                            detectDragGestures(
                                onDragStart = { p ->
                                    if (currentTool == Tool.DRAW) {
                                        current = listOf(Offset(p.x / imgW, p.y / imgH))
                                        return@detectDragGestures
                                    }
                                    startCrop = crop
                                    total = Offset.Zero
                                    grab = grabAt(crop, p, imgW, imgH, handlePx)
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    if (currentTool == Tool.DRAW) {
                                        val p = change.position
                                        current = current + Offset((p.x / imgW).coerceIn(0f, 1f), (p.y / imgH).coerceIn(0f, 1f))
                                        return@detectDragGestures
                                    }
                                    total += drag
                                    val dx = total.x / imgW
                                    val dy = total.y / imgH
                                    crop = when (grab) {
                                        Grab.NONE -> crop
                                        Grab.MOVE -> move(startCrop, dx, dy)
                                        else -> resize(startCrop, grab, dx, dy, currentShape.ratio?.let(::k))
                                    }
                                },
                                onDragEnd = {
                                    if (currentTool == Tool.DRAW && current.isNotEmpty()) {
                                        marks.add(Mark(current, penColour, penSize))
                                        current = emptyList()
                                    }
                                    grab = Grab.NONE
                                },
                                onDragCancel = { current = emptyList(); grab = Grab.NONE },
                            )
                        },
                    ) {
                        fun px(points: List<Offset>) = points.map { Offset(it.x * size.width, it.y * size.height) }
                        (marks + listOfNotNull(current.takeIf { it.isNotEmpty() }?.let { Mark(it, penColour, penSize) })).forEach { m ->
                            drawPath(
                                smoothPath(px(m.points)), m.colour,
                                style = Stroke(m.width * size.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                        val r = Rect(crop.left * size.width, crop.top * size.height, crop.right * size.width, crop.bottom * size.height)
                        val shade = Color.Black.copy(alpha = if (tool == Tool.CROP) 0.55f else 0.35f)
                        drawRect(shade, Offset.Zero, Size(size.width, r.top))
                        drawRect(shade, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
                        drawRect(shade, Offset(0f, r.top), Size(r.left, r.height))
                        drawRect(shade, Offset(r.right, r.top), Size(size.width - r.right, r.height))
                        drawRect(Color.White, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
                        if (tool == Tool.CROP) {
                            for (i in 1..2) {
                                val x = r.left + r.width * i / 3
                                val y = r.top + r.height * i / 3
                                drawLine(Color.White.copy(alpha = 0.5f), Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
                                drawLine(Color.White.copy(alpha = 0.5f), Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
                            }
                            listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach { drawCircle(Color.White, 9.dp.toPx(), it) }
                            // side handles
                            val bar = 22.dp.toPx()
                            val t = 4.dp.toPx()
                            listOf(Offset(r.center.x, r.top), Offset(r.center.x, r.bottom)).forEach {
                                drawLine(Color.White, it - Offset(bar / 2, 0f), it + Offset(bar / 2, 0f), t, StrokeCap.Round)
                            }
                            listOf(Offset(r.left, r.center.y), Offset(r.right, r.center.y)).forEach {
                                drawLine(Color.White, it - Offset(0f, bar / 2), it + Offset(0f, bar / 2), t, StrokeCap.Round)
                            }
                        }
                    }
                }
            }

            // Tool-specific controls
            if (tool == Tool.CROP) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    CropShape.entries.forEach { s ->
                        FilterChip(
                            selected = shape == s,
                            onClick = {
                                shape = s
                                crop = s.ratio?.let { centered(k(it)) } ?: Rect(0f, 0f, 1f, 1f)
                            },
                            label = { Text(s.label, color = Color.White) },
                        )
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PenColours.forEach { c ->
                        Box(
                            Modifier.size(30.dp).background(c, CircleShape)
                                .border(if (c == penColour) 3.dp else 1.dp, if (c == penColour) Color(0xFF8AB4F8) else Color.Gray, CircleShape)
                                .clickable { penColour = c },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PenSizes.forEachIndexed { i, w ->
                        FilterChip(
                            selected = penSize == w, onClick = { penSize = w },
                            label = { Text(listOf("Thin", "Medium", "Thick")[i], color = Color.White) },
                        )
                    }
                    IconButton(onClick = { if (marks.isNotEmpty()) marks.removeAt(marks.lastIndex) }, enabled = marks.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (marks.isNotEmpty()) Color.White else Color.Gray)
                    }
                    IconButton(onClick = { marks.clear() }, enabled = marks.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, "Clear drawing", tint = if (marks.isNotEmpty()) Color.White else Color.Gray)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                FilterChip(
                    selected = tool == Tool.CROP, onClick = { tool = Tool.CROP },
                    leadingIcon = { Icon(Icons.Filled.Crop, null, tint = Color.White) },
                    label = { Text("Crop", color = Color.White) },
                )
                FilterChip(
                    selected = tool == Tool.DRAW, onClick = { tool = Tool.DRAW },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, tint = Color.White) },
                    label = { Text("Draw", color = Color.White) },
                )
            }
        }
    }
}

/** What a crop drag holds on to. */
private enum class Grab(val left: Boolean = false, val top: Boolean = false, val right: Boolean = false, val bottom: Boolean = false) {
    NONE, MOVE,
    TOP_LEFT(left = true, top = true), TOP_RIGHT(right = true, top = true),
    BOTTOM_LEFT(left = true, bottom = true), BOTTOM_RIGHT(right = true, bottom = true),
    LEFT(left = true), TOP(top = true), RIGHT(right = true), BOTTOM(bottom = true),
}

/** Corners first, then sides (each within [handle] px), then inside the box to move it. */
private fun grabAt(c: Rect, p: Offset, w: Float, h: Float, handle: Float): Grab {
    val r = Rect(c.left * w, c.top * h, c.right * w, c.bottom * h)
    val corners = listOf(Grab.TOP_LEFT to r.topLeft, Grab.TOP_RIGHT to r.topRight, Grab.BOTTOM_LEFT to r.bottomLeft, Grab.BOTTOM_RIGHT to r.bottomRight)
    corners.minBy { (it.second - p).getDistance() }.let { (g, o) -> if ((o - p).getDistance() <= handle) return g }
    val inY = p.y in r.top..r.bottom
    val inX = p.x in r.left..r.right
    return when {
        inY && kotlin.math.abs(p.x - r.left) <= handle / 2 -> Grab.LEFT
        inY && kotlin.math.abs(p.x - r.right) <= handle / 2 -> Grab.RIGHT
        inX && kotlin.math.abs(p.y - r.top) <= handle / 2 -> Grab.TOP
        inX && kotlin.math.abs(p.y - r.bottom) <= handle / 2 -> Grab.BOTTOM
        inX && inY -> Grab.MOVE
        else -> Grab.NONE
    }
}

/** Largest centred crop whose height fraction is [k] times its width fraction. */
private fun centered(k: Float): Rect {
    var w = 1f
    var h = k
    if (h > 1f) { h = 1f; w = 1f / k }
    return Rect((1f - w) / 2, (1f - h) / 2, (1f + w) / 2, (1f + h) / 2)
}

private fun move(c: Rect, dx: Float, dy: Float): Rect {
    val x = dx.coerceIn(-c.left, 1f - c.right)
    val y = dy.coerceIn(-c.top, 1f - c.bottom)
    return c.translate(x, y)
}

/**
 * The start crop [c] with the grabbed edges moved by the total drag (dx, dy), within the image and at least
 * [MIN_SIDE]. With [k] the shape keeps height = k × width; a side drag then grows the other axis around the centre.
 */
private fun resize(c: Rect, g: Grab, dx: Float, dy: Float, k: Float?): Rect {
    var l = c.left
    var t = c.top
    var r = c.right
    var b = c.bottom
    if (g.left) l = (c.left + dx).coerceIn(0f, c.right - MIN_SIDE)
    if (g.right) r = (c.right + dx).coerceIn(c.left + MIN_SIDE, 1f)
    if (g.top) t = (c.top + dy).coerceIn(0f, c.bottom - MIN_SIDE)
    if (g.bottom) b = (c.bottom + dy).coerceIn(c.top + MIN_SIDE, 1f)
    if (k == null) return Rect(l, t, r, b)

    // Keep the ratio: anchor the side(s) not being dragged.
    val horizontal = g.left || g.right
    var w = r - l
    var h = b - t
    if (horizontal && (g.top || g.bottom)) {
        // corner: follow whichever axis moved further
        if (h < w * k) h = w * k else w = h / k
    } else if (horizontal) {
        h = w * k
    } else {
        w = h / k
    }
    // Fit inside the image around the fixed edges.
    val ax = if (g.left) c.right else if (g.right) c.left else c.center.x
    val ay = if (g.top) c.bottom else if (g.bottom) c.top else c.center.y
    val maxW = when { g.left -> ax; g.right -> 1f - ax; else -> 2 * minOf(ax, 1f - ax) }
    val maxH = when { g.top -> ay; g.bottom -> 1f - ay; else -> 2 * minOf(ay, 1f - ay) }
    if (w > maxW) { w = maxW; h = w * k }
    if (h > maxH) { h = maxH; w = h / k }
    val nl = when { g.left -> ax - w; g.right -> ax; else -> ax - w / 2 }
    val nt = when { g.top -> ay - h; g.bottom -> ay; else -> ay - h / 2 }
    return Rect(nl, nt, nl + w, nt + h)
}
