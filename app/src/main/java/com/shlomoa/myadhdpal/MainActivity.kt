package com.shlomoa.myadhdpal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.shlomoa.myadhdpal.ui.MyAdhdPalApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyAdhdPalApp() }
    }
}
