package com.lastcalled.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lastcalled.app.ui.navigation.LastCalledApp
import com.lastcalled.app.ui.theme.LastCalledTheme

/** Single host activity; screens are Compose destinations (see AppNavHost). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LastCalledTheme {
                LastCalledApp(appContainer)
            }
        }
    }
}
