package com.shlomoa.myadhdpal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.shlomoa.myadhdpal.R
import com.shlomoa.myadhdpal.ui.theme.MyAdhdPalTheme

@Composable
fun MyAdhdPalApp() {
    MyAdhdPalTheme {
        AppScaffold {
            HomeScreen()
        }
    }
}

@Composable
private fun HomeScreen() {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            item {
                Text(
                    text = stringResource(R.string.home_greeting),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun MyAdhdPalAppPreview() {
    MyAdhdPalApp()
}
