package com.shlomoa.myadhdpal.wear.monitor

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.shlomoa.myadhdpal.wear.R
import com.shlomoa.myadhdpal.wear.WatchApplication
import com.shlomoa.myadhdpal.wear.ui.theme.MyAdhdPalTheme
import kotlin.math.roundToInt

/**
 * The Monitoring screen: it keeps the screen on while it is in front (about one reading per second reaches the app
 * then, experiment E2), starts the [MonitoringService], and shows the latest reading, the baseline, the threshold and
 * the stress state with an "I'm OK" button. Started from the app or, in debug builds, with `adb shell am start`.
 */
class MonitoringActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startForegroundService(Intent(this, MonitoringService::class.java))
        val hub = (application as WatchApplication).graph.monitorHub
        setContent { MyAdhdPalTheme { AppScaffold { MonitoringScreen(hub) } } }
    }
}

@Composable
private fun MonitoringScreen(hub: MonitorHub) {
    var state by remember { mutableStateOf(hub.state) }
    DisposableEffect(hub) {
        val listener: (MonitorState) -> Unit = { state = it }
        hub.addListener(listener)
        state = hub.state
        onDispose { hub.removeListener(listener) }
    }
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            item { Text(stringResource(R.string.monitoring_title), style = MaterialTheme.typography.titleMedium) }
            item {
                Text(readingText(state), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            }
            item {
                Text(
                    stringResource(R.string.monitoring_limits, baselineText(state), state.thresholdBpm.roundToInt()),
                    textAlign = TextAlign.Center
                )
            }
            item {
                Text(
                    if (state.stress) {
                        stringResource(
                            R.string.monitoring_stress,
                            state.level
                        )
                    } else {
                        stringResource(R.string.monitoring_calm)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            }
            if (state.stress) item { Button(onClick = hub::imOk) { Text(stringResource(R.string.monitoring_im_ok)) } }
        }
    }
}

private fun readingText(state: MonitorState): String = state.latestBpm?.roundToInt()?.toString() ?: "--"

@Composable
private fun baselineText(state: MonitorState): String =
    state.baselineBpm?.roundToInt()?.toString() ?: stringResource(R.string.monitoring_no_baseline)
