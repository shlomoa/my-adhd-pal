package com.shlomoa.myadhdpal.phone.ui.screens

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every phone screen from the real Compose code with fixed example data and writes one PNG each to
 * `phone/build/screens/<id>.png`, plus the page `index.md`. Copy them to `docs/demo/phone/` to publish (docs/demo.md).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val outDir = File("build/screens").apply { mkdirs() }

    @Test
    fun `TC-A-146 every catalogued phone screen is rendered to a PNG that shows something`() {
        val screens = PhoneScreens.all
        assertTrue("the catalogue is empty", screens.isNotEmpty())
        assertEquals("screen ids are unique", screens.size, screens.map { it.id }.toSet().size)
        var shown by mutableStateOf(screens.first())
        compose.setContent { MaterialTheme { key(shown.id) { shown.content() } } }
        for (screen in screens) {
            shown = screen
            screen.openTab?.let { compose.onNodeWithText(it).performClick() }
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val file = File(outDir, "${screen.id}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("${screen.id}: image is empty", file.length() > 0)
            assertTrue("${screen.id}: image is one flat colour", colourCount(bitmap) >= MIN_COLOURS)
        }
    }

    @Test
    fun `TC-A-147 the index page lists every screen with its title and image`() {
        val markdown = PhoneScreens.indexMarkdown()
        for (screen in PhoneScreens.all) {
            assertTrue("${screen.id} title missing", "## ${screen.title}" in markdown)
            assertTrue("${screen.id} image missing", "![${screen.title}](${screen.id}.png)" in markdown)
        }
        File(outDir, "index.md").writeText(markdown)
    }

    private fun colourCount(bitmap: Bitmap): Int {
        val seen = HashSet<Int>()
        for (x in 0 until bitmap.width step SAMPLE_STEP) {
            for (y in 0 until bitmap.height step SAMPLE_STEP) seen += bitmap.getPixel(x, y)
        }
        return seen.size
    }

    private companion object {
        const val MIN_COLOURS = 3
        const val SAMPLE_STEP = 7
    }
}
