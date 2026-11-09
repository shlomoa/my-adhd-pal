package com.shlomoa.myadhdpal.phone.ui.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shlomoa.myadhdpal.phone.R

private const val NOTIFICATION_PERMISSION_SDK = 33
private const val FULL_SCREEN_PERMISSION_SDK = 34

/**
 * Whether the phone alert (`docs/phone-alert.md`) may show: without the notification permission no alert appears at
 * all, and without the full-screen permission it is only a heads-up notification.
 */
data class AlertPermissions(val notifications: Boolean, val fullScreen: Boolean) {
    val allGranted: Boolean get() = notifications && fullScreen

    companion object {
        /** Before Android 13 notifications need no permission, and before Android 14 full-screen alerts are allowed. */
        fun of(sdk: Int, notificationsGranted: Boolean, canUseFullScreenIntent: Boolean) = AlertPermissions(
            notifications = sdk < NOTIFICATION_PERMISSION_SDK || notificationsGranted,
            fullScreen = sdk < FULL_SCREEN_PERMISSION_SDK || canUseFullScreenIntent
        )
    }
}

/** Asks for each missing alert permission; shows nothing once both are granted. */
@Composable
fun AlertPermissionsCard(
    state: AlertPermissions,
    onAllowNotifications: () -> Unit,
    onAllowFullScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.allGranted) return
    Card(modifier.padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.permissions_text), style = MaterialTheme.typography.bodyMedium)
            if (!state.notifications) {
                Button(onClick = onAllowNotifications) { Text(stringResource(R.string.permissions_notifications)) }
            }
            if (!state.fullScreen) {
                Button(onClick = onAllowFullScreen) { Text(stringResource(R.string.permissions_full_screen)) }
            }
        }
    }
}
