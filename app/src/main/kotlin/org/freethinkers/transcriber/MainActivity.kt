package org.freethinkers.transcriber

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.freethinkers.transcriber.ui.App
import org.freethinkers.transcriber.ui.theme.TranscriberTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TranscriberTheme {
                App()
            }
        }
    }
}