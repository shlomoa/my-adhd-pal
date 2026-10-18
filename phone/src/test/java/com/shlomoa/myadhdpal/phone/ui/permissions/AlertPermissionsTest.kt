package com.shlomoa.myadhdpal.phone.ui.permissions

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The permissions the phone alert needs: the state of each and the card that asks for the missing ones. */
@RunWith(RobolectricTestRunner::class)
class AlertPermissionsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `TC-A-136 below Android 13 and 14 the permissions count as granted`() {
        assertEquals(
            AlertPermissions(notifications = true, fullScreen = true),
            AlertPermissions.of(sdk = 32, notificationsGranted = false, canUseFullScreenIntent = false)
        )
        assertEquals(
            AlertPermissions(notifications = false, fullScreen = true),
            AlertPermissions.of(sdk = 33, notificationsGranted = false, canUseFullScreenIntent = false)
        )
        assertEquals(
            AlertPermissions(notifications = true, fullScreen = false),
            AlertPermissions.of(sdk = 34, notificationsGranted = true, canUseFullScreenIntent = false)
        )
        assertTrue(AlertPermissions.of(34, notificationsGranted = true, canUseFullScreenIntent = true).allGranted)
    }

    @Test
    fun `TC-A-137 the card has one button per missing permission`() {
        var asked = ""
        compose.setContent {
            MaterialTheme {
                AlertPermissionsCard(
                    state = AlertPermissions(notifications = false, fullScreen = false),
                    onAllowNotifications = { asked += "n" },
                    onAllowFullScreen = { asked += "f" }
                )
            }
        }
        compose.onNodeWithText("Allow notifications").performClick()
        compose.onNodeWithText("Allow full-screen alerts").performClick()
        assertEquals("nf", asked)
    }

    @Test
    fun `TC-A-138 the card shows nothing when both permissions are granted`() {
        compose.setContent {
            MaterialTheme {
                AlertPermissionsCard(AlertPermissions(notifications = true, fullScreen = true), {}, {})
            }
        }
        compose.onNodeWithText("Allow notifications").assertDoesNotExist()
        compose.onNodeWithText("Allow full-screen alerts").assertDoesNotExist()
    }
}
