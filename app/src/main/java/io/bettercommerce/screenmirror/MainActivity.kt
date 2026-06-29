package io.bettercommerce.screenmirror

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.bettercommerce.screenmirror.ui.ScreenMirrorApp
import io.bettercommerce.screenmirror.ui.theme.ScreenMirrorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenMirrorTheme {
                ScreenMirrorApp()
            }
        }
    }
}
