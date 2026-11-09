package com.shlomoa.myadhdpal.phone.compat

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Toolchain sample (issue 113): Compose UI tests run under Robolectric in `:phone:testDebugUnitTest`. */
@RunWith(RobolectricTestRunner::class)
class ComposeUiTestSampleTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TC-S-144 a Compose UI test finds and clicks a node under Robolectric`() {
        var clicks = 0
        compose.setContent { androidx.compose.material3.TextButton(onClick = { clicks++ }) { Text("Done") } }
        compose.onNodeWithText("Done").performClick()
        org.junit.Assert.assertEquals(1, clicks)
    }
}
