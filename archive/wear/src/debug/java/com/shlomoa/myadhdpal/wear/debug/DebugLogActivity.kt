package com.shlomoa.myadhdpal.wear.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay

private const val REFRESH_MS = 1_000L

/** Debug-only log view: `adb shell am start -n com.shlomoa.myadhdpal/.wear.debug.DebugLogActivity`. */
class DebugLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DebugLogScreen() }
    }
}

@Composable
private fun DebugLogScreen() {
    var entries by remember { mutableStateOf(DebugLog.sink.snapshot()) }
    LaunchedEffect(Unit) {
        while (true) {
            entries = DebugLog.sink.snapshot()
            delay(REFRESH_MS)
        }
    }
    ScalingLazyColumn {
        items(entries.asReversed()) { Text("${it.level.name.first()} ${it.tag}: ${it.message}") }
    }
}
