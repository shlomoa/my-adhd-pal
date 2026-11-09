package com.shlomoa.myadhdpal.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.shlomoa.myadhdpal.protocol.Prompt
import com.shlomoa.myadhdpal.protocol.PromptOption
import com.shlomoa.myadhdpal.wear.ui.theme.MyAdhdPalTheme

/** A prompt from the phone with one button per option; [onChoose] gets the key of the tapped option. */
@Composable
fun SelectionScreen(prompt: Prompt, onChoose: (String) -> Unit) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            item { Text(prompt.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
            prompt.options.forEach { option ->
                item {
                    Button(onClick = { onChoose(option.key) }, modifier = Modifier.fillMaxWidth()) {
                        Text(option.label)
                    }
                }
            }
        }
    }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun SelectionScreenPreview() {
    MyAdhdPalTheme {
        SelectionScreen(
            Prompt("Did you eat?", listOf(PromptOption("eat", "Eat now"), PromptOption("later", "Later")))
        ) {
        }
    }
}
