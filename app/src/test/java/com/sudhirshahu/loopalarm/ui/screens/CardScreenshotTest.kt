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
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.sudhirshahu.loopalarm.data.Accent
import com.sudhirshahu.loopalarm.data.Alarm
import com.sudhirshahu.loopalarm.data.IntervalUnit
import com.sudhirshahu.loopalarm.data.ThemeMode
import com.sudhirshahu.loopalarm.ui.theme.LoopAlarmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders sample reminder cards to app/build/screenshots/ so the design can be checked without a phone. */
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

    private fun shoot(name: String, mode: ThemeMode) {
        compose.setContent {
            LoopAlarmTheme(mode, Accent.INDIGO) {
                Column(
                    Modifier.width(411.dp).background(MaterialTheme.colorScheme.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    samples.forEach { (a, next) -> AlarmCard(a, next, now, false, { _, _ -> }, {}, {}, {}, {}) }
                }
            }
        }
        compose.waitForIdle()
        // captureToImage() waits for a hardware redraw that never comes under Robolectric, so draw the view directly.
        val size = compose.onRoot().fetchSemanticsNode().size
        val view = compose.activity.findViewById<View>(android.R.id.content)
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(full))
        val bmp = Bitmap.createBitmap(full, 0, 0, size.width.coerceAtMost(full.width), size.height.coerceAtMost(full.height))
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun cardsLight() = shoot("cards-light", ThemeMode.LIGHT)

    @Test fun cardsDark() = shoot("cards-dark", ThemeMode.DARK)
}
