package org.freethinkers.transcriber.ui.transcript

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TranscriptScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Расшифровка")
        Text("Здесь появятся реплики с говорящими и временными метками.")
        Button(onClick = { /* TODO Add the Android system save-file flow. */ }, enabled = false) {
            Text("Экспортировать JSON")
        }
    }
}