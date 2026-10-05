package com.sudhirshahu.loopalarm.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Crop shapes offered above the image. [ratio] is width / height in pixels; null means free. */
private enum class CropShape(val label: String, val ratio: Float?) {
    FREE("Free", null), SQUARE("Square", 1f), WIDE("Wide 2:1", 2f)
}

private const val MIN_SIDE = 0.08f

/**
 * Full-screen cropper. Drag inside the box to move it, drag a corner to resize.
 * [onDone] gets the crop as fractions of the image: left, top, right, bottom.
 */
@Composable
fun CropDialog(source: Bitmap, onCancel: () -> Unit, onDone: (FloatArray) -> Unit) {
    val image = remember(source) { source.asImageBitmap() }
    var shape by remember { mutableStateOf(CropShape.FREE) }
    var crop by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    // Height of a crop fraction per unit of width fraction for a given pixel ratio.
    fun k(ratio: Float) = source.width / (source.height * ratio)

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel", color = Color.White) }
                Text("Crop picture", color = Color.White, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Button(onClick = { onDone(floatArrayOf(crop.left, crop.top, crop.right, crop.bottom)) }) { Text("Use") }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val scale = minOf(boxW / source.width, boxH / source.height)
                val imgW = source.width * scale
                val imgH = source.height * scale
                val handlePx = with(density) { 36.dp.toPx() }
                val currentShape by rememberUpdatedState(shape)

                Box(Modifier.size(with(density) { imgW.toDp() }, with(density) { imgH.toDp() })) {
                    Image(image, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(imgW, imgH) {
                            var mode = -1 // 0..3 = corner TL, TR, BL, BR; 4 = move; -1 = nothing
                            detectDragGestures(
                                onDragStart = { p ->
                                    val c = crop
                                    val corners = listOf(
                                        Offset(c.left * imgW, c.top * imgH), Offset(c.right * imgW, c.top * imgH),
                                        Offset(c.left * imgW, c.bottom * imgH), Offset(c.right * imgW, c.bottom * imgH),
                                    )
                                    val nearest = corners.indices.minBy { (corners[it] - p).getDistance() }
                                    mode = when {
                                        (corners[nearest] - p).getDistance() <= handlePx -> nearest
                                        p.x / imgW in c.left..c.right && p.y / imgH in c.top..c.bottom -> 4
                                        else -> -1
                                    }
                                },
                                onDrag = { change, drag ->
                                    change.consume()
                                    val dx = drag.x / imgW
                                    val dy = drag.y / imgH
                                    crop = when (mode) {
                                        4 -> move(crop, dx, dy)
                                        in 0..3 -> resize(crop, mode, dx, dy, currentShape.ratio?.let(::k))
                                        else -> crop
                                    }
                                },
                            )
                        },
                    ) {
                        val r = Rect(crop.left * size.width, crop.top * size.height, crop.right * size.width, crop.bottom * size.height)
                        val shade = Color.Black.copy(alpha = 0.55f)
                        drawRect(shade, Offset.Zero, Size(size.width, r.top))
                        drawRect(shade, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
                        drawRect(shade, Offset(0f, r.top), Size(r.left, r.height))
                        drawRect(shade, Offset(r.right, r.top), Size(size.width - r.right, r.height))
                        drawRect(Color.White, r.topLeft, r.size, style = Stroke(2.dp.toPx()))
                        for (i in 1..2) {
                            val x = r.left + r.width * i / 3
                            val y = r.top + r.height * i / 3
                            drawLine(Color.White.copy(alpha = 0.5f), Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.5f), Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
                        }
                        listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach { drawCircle(Color.White, 8.dp.toPx(), it) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
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
        }
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

/** Drags [corner] by (dx, dy), keeping the opposite corner fixed. With [k], keeps height = k × width. */
private fun resize(c: Rect, corner: Int, dx: Float, dy: Float, k: Float?): Rect {
    val leftSide = corner == 0 || corner == 2
    val topSide = corner == 0 || corner == 1
    // Fixed corner and the free one after the drag.
    val ax = if (leftSide) c.right else c.left
    val ay = if (topSide) c.bottom else c.top
    var x = ((if (leftSide) c.left else c.right) + dx).coerceIn(0f, 1f)
    var y = ((if (topSide) c.top else c.bottom) + dy).coerceIn(0f, 1f)
    var w = (if (leftSide) ax - x else x - ax).coerceAtLeast(MIN_SIDE)
    var h = (if (topSide) ay - y else y - ay).coerceAtLeast(MIN_SIDE)
    val maxW = if (leftSide) ax else 1f - ax
    val maxH = if (topSide) ay else 1f - ay
    if (k != null) {
        h = w * k
        if (h > maxH) { h = maxH; w = h / k }
        if (w > maxW) { w = maxW; h = w * k }
    } else {
        w = w.coerceAtMost(maxW)
        h = h.coerceAtMost(maxH)
    }
    x = if (leftSide) ax - w else ax + w
    y = if (topSide) ay - h else ay + h
    return Rect(minOf(ax, x), minOf(ay, y), maxOf(ax, x), maxOf(ay, y))
}
