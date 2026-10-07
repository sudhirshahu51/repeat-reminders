package com.sudhirshahu.loopalarm.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.sudhirshahu.loopalarm.data.Accent
import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.ImageStore
import com.sudhirshahu.loopalarm.data.IntervalUnit
import com.sudhirshahu.loopalarm.data.ThemeMode
import com.sudhirshahu.loopalarm.ring.Ringing
import com.sudhirshahu.loopalarm.ui.RingScreen
import com.sudhirshahu.loopalarm.ui.theme.LoopAlarmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders sample screens to app/build/screenshots/ so the design can be checked without a phone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class CardScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val samples = listOf(
        Alarm(id = 1, name = "Drink water", intervalValue = 30, startMinute = 9 * 60, notes = "Full glass\nRefill the bottle") to now + 25 * 60_000L,
        Alarm(id = 2, name = "Medicine", icon = "💊", repeating = false, startMinute = 21 * 60, notes = "Take after dinner") to now + 6 * 3_600_000L,
        Alarm(id = 3, name = "Stretch", intervalValue = 1, intervalUnit = IntervalUnit.HOURS, startMinute = 10 * 60, enabled = false) to null,
    )

    private fun shoot(name: String, mode: ThemeMode, content: @Composable () -> Unit) {
        // The alarm screen has an endless pulse animation, so the clock is moved by hand instead of waiting for idle.
        compose.mainClock.autoAdvance = false
        compose.setContent { LoopAlarmTheme(mode, Accent.INDIGO) { content() } }
        compose.mainClock.advanceTimeBy(500)
        // captureToImage() waits for a hardware redraw that never comes under Robolectric, so draw the view directly.
        val size = compose.onRoot().fetchSemanticsNode().size
        val view = compose.activity.findViewById<View>(android.R.id.content)
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(full))
        val bmp = Bitmap.createBitmap(full, 0, 0, size.width.coerceAtMost(full.width), size.height.coerceAtMost(full.height))
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Cards() {
        Column(
            Modifier.width(411.dp).background(MaterialTheme.colorScheme.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            samples.forEach { (a, next) -> AlarmCard(a, next, now, false, { _, _ -> }, {}, {}, {}, {}) }
        }
    }

    @Test fun cardsLight() = shoot("cards-light", ThemeMode.LIGHT) { Cards() }

    @Test fun cardsDark() = shoot("cards-dark", ThemeMode.DARK) { Cards() }

    private val medicine = Alarm(
        id = 2, name = "Medicine", intervalValue = 30, startMinute = 21 * 60, groupName = "Health",
        notes = "[x] Take after dinner\n[ ] One tablet with a full glass of water\n[ ] Refill the box on Sunday",
        soundTitle = "Classic beep", snoozeMinutes = 10,
    )

    /** The Notes section of the editor, as numbered points. */
    @Test fun notesEditor() = shoot("notes-editor", ThemeMode.LIGHT) {
        Column(Modifier.width(411.dp).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
            com.sudhirshahu.loopalarm.ui.components.NotesEditor("1. Take after dinner\n2. One tablet with water\n3. Refill the box on Sunday") {}
        }
    }

    @Test fun detailsPage() = shoot("details-page", ThemeMode.LIGHT) {
        ReminderDetailsScreen(medicine, false, {}, {}, { _, _ -> }, {}, {}, {})
    }

    /** Two drawn sample pictures saved like real ones, so the page shows its picture section. */
    private fun samplePictures(): List<String> {
        val context = compose.activity
        fun picture(w: Int, h: Int, draw: (Canvas, android.graphics.Paint) -> Unit): String {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            draw(Canvas(bmp), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG))
            return ImageStore.saveCropped(context, bmp)!!
        }
        // A medicine box on a table
        val box = picture(1080, 600) { c, p ->
            c.drawColor(0xFFE3F2FD.toInt())
            p.color = 0xFFBCAAA4.toInt(); c.drawRect(0f, 430f, 1080f, 600f, p)
            p.color = 0xFFFFFFFF.toInt(); c.drawRoundRect(340f, 120f, 740f, 470f, 28f, 28f, p)
            p.color = 0xFFE53935.toInt(); c.drawRect(505f, 190f, 575f, 400f, p); c.drawRect(435f, 260f, 645f, 330f, p)
            p.color = 0xFF1E88E5.toInt(); p.textSize = 54f; c.drawText("Vitamin D", 430f, 455f, p)
        }
        // A glass of water
        val glass = picture(1080, 600) { c, p ->
            c.drawColor(0xFFFFF8E1.toInt())
            p.color = 0xFFB3E5FC.toInt(); c.drawRect(420f, 220f, 660f, 520f, p)
            p.style = android.graphics.Paint.Style.STROKE; p.strokeWidth = 10f; p.color = 0xFF607D8B.toInt()
            c.drawRect(420f, 100f, 660f, 520f, p)
        }
        return listOf(box, glass)
    }

    @Test
    @Config(qualifiers = "w411dp-h1900dp-xxhdpi")
    fun detailsPageWithPictures() {
        val withPictures = medicine.withImages(samplePictures())
        shoot("details-page-full", ThemeMode.LIGHT) {
            ReminderDetailsScreen(withPictures, false, {}, {}, { _, _ -> }, {}, {}, {})
        }
    }

    /** The Pictures section of the editor: thumbnails with their pencil and ✕, Add Pictures and Take Photo. */
    @Test
    fun picturesSection() {
        org.robolectric.Shadows.shadowOf(compose.activity.packageManager)
            .setSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY, true)
        val names = samplePictures()
        shoot("pictures-section", ThemeMode.LIGHT) {
            Column(Modifier.width(411.dp).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                PictureSection(names) {}
            }
        }
    }

    /** The picture editor in Draw mode with a circle and an arrow marked on a sample photo. */
    @Test
    fun pictureEditor() {
        val bmp = Bitmap.createBitmap(1080, 720, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(0xFFE3F2FD.toInt())
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            p.color = 0xFFBCAAA4.toInt(); drawRect(0f, 520f, 1080f, 720f, p)
            p.color = 0xFFFFFFFF.toInt(); drawRoundRect(340f, 170f, 740f, 560f, 28f, 28f, p)
            p.color = 0xFFE53935.toInt(); drawRect(505f, 240f, 575f, 460f, p); drawRect(435f, 315f, 645f, 385f, p)
        }
        val circle = (0..40).map { i ->
            val t = i / 40.0 * 2 * Math.PI
            androidx.compose.ui.geometry.Offset((0.5 + 0.24 * Math.cos(t)).toFloat(), (0.5 + 0.34 * Math.sin(t)).toFloat())
        }
        val arrow = listOf(0.08f to 0.12f, 0.2f to 0.2f, 0.3f to 0.3f).map { androidx.compose.ui.geometry.Offset(it.first, it.second) }
        val head = listOf(0.22f to 0.3f, 0.3f to 0.3f, 0.3f to 0.2f).map { androidx.compose.ui.geometry.Offset(it.first, it.second) }
        val marks = listOf(
            com.sudhirshahu.loopalarm.ui.components.Mark(circle, androidx.compose.ui.graphics.Color(0xFFFFCA28), 0.012f),
            com.sudhirshahu.loopalarm.ui.components.Mark(arrow, androidx.compose.ui.graphics.Color(0xFFE53935), 0.012f),
            com.sudhirshahu.loopalarm.ui.components.Mark(head, androidx.compose.ui.graphics.Color(0xFFE53935), 0.012f),
        )
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LoopAlarmTheme(ThemeMode.DARK, Accent.INDIGO) {
                com.sudhirshahu.loopalarm.ui.components.CropDialog(bmp, {}, { _, _ -> }, startDrawing = true, initialMarks = marks)
            }
        }
        compose.mainClock.advanceTimeBy(500)
        // The editor is a dialog: draw its own window.
        val view = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
        val out = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(out))
        File(File("build/screenshots").apply { mkdirs() }, "picture-editor.png").outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun alarmScreen() = shoot("alarm-screen", ThemeMode.DARK) {
        RingScreen(
            Ringing(names = "Medicine", firedAt = now, snoozeMinutes = 10, subtitle = "", alarmIds = listOf(2)),
            listOf(medicine), use24 = false, onSnooze = {}, onDismiss = {},
        )
    }
}
