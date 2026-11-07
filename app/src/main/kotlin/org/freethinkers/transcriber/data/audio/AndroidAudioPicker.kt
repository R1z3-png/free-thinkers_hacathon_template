package org.freethinkers.transcriber.data.audio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/** Creates a launcher for the Android system document picker without broad storage access. */
@Composable
fun rememberAndroidAudioPicker(onAudioPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onAudioPicked(uri)
    }

    return { launcher.launch(arrayOf("audio/*")) }
}
