package com.shlomoa.myadhdpal.phone.alert

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shlomoa.myadhdpal.phone.R
import com.shlomoa.myadhdpal.phone.data.HistoryEntry
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

private val ALERT_RED = Color(0xFFB00020)
private val ALERT_DARK = Color(0xFF3B0008)

/**
 * The full-screen alert (`docs/phone-alert.md`): shown over the lock screen with the screen switched on. One big
 * button, "I'm OK", stops the phone's vibration and sound. The background alternates when flashing is on, at most
 * 3 times a second.
 */
@AndroidEntryPoint
class AlertActivity : ComponentActivity() {
    @Inject
    lateinit var raiser: AlertRaiser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val value = intent.getDoubleExtra(EXTRA_VALUE, 0.0)
        val threshold = intent.getDoubleExtra(EXTRA_THRESHOLD, 0.0)
        val flashMillis = intent.getLongExtra(EXTRA_FLASH_MILLIS, NO_FLASH)
        setContent {
            MaterialTheme {
                var bright by remember { mutableStateOf(true) }
                if (flashMillis != NO_FLASH) {
                    LaunchedEffect(flashMillis) {
                        while (true) {
                            delay(flashMillis / 2)
                            bright = !bright
                        }
                    }
                }
                AlertScreen(value, threshold, bright, onOk = {
                    raiser.dismiss()
                    finish()
                })
            }
        }
    }

    companion object {
        private const val EXTRA_VALUE = "value"
        private const val EXTRA_THRESHOLD = "threshold"
        private const val EXTRA_FLASH_MILLIS = "flash_millis"
        private const val NO_FLASH = 0L

        fun intent(context: Context, alert: HistoryEntry.AlertRaised, plan: AlertPlan): Intent =
            Intent(context, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_VALUE, alert.value)
                .putExtra(EXTRA_THRESHOLD, alert.threshold)
                .putExtra(EXTRA_FLASH_MILLIS, plan.flash?.cycleMillis ?: NO_FLASH)
    }
}

/** Stateless: [bright] picks the background colour, so a test can show both without a running animation. */
@Composable
fun AlertScreen(value: Double, threshold: Double, bright: Boolean, onOk: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(if (bright) ALERT_RED else ALERT_DARK).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.alert_title), color = Color.White, fontSize = 32.sp, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.alert_text, value, threshold),
            color = Color.White,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
        Button(onOk, modifier = Modifier.fillMaxSize(fraction = 0.35f)) {
            Text(stringResource(R.string.alert_ok), fontSize = 28.sp)
        }
    }
}
