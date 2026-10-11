package com.shlomoa.myadhdpal.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.shlomoa.myadhdpal.wear.ui.MyAdhdPalApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyAdhdPalApp() }
    }
}
