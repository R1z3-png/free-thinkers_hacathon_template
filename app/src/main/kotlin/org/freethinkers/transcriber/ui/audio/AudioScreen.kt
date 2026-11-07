package org.freethinkers.transcriber.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.freethinkers.transcriber.domain.model.AudioFile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AudioScreen(
    state: AudioUiState,
    onChooseAudio: () -> Unit,
    onClearSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Аудиозапись")
        Text("Выберите один MP3-файл для текущей сессии.")
        Button(onClick = onChooseAudio) {
            Text(if (state.audioFile == null) "Выбрать MP3" else "Заменить MP3")
        }

        state.audioFile?.let { audioFile ->
            AudioFileDetails(audioFile)
            TextButton(onClick = { showDeleteConfirmation = true }) {
                Text("Удалить запись")
            }
        } ?: Text("Файл не выбран")

        state.errorMessage?.let { message -> Text(message) }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Удалить текущую запись?") },
            text = { Text("Текущая запись и её расшифровка будут удалены. Исходный MP3 останется на устройстве.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onClearSession()
                    },
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun AudioFileDetails(audioFile: AudioFile) {
    Text("Файл: ${audioFile.fileName}")
    Text("Длительность: ${formatDuration(audioFile.durationMs)}")
    Text("Добавлен: ${formatImportTime(audioFile.importedAtEpochMs)}")
}

private fun formatDuration(durationMs: Long?): String {
    if (durationMs == null) return "Неизвестна"
    val totalSeconds = durationMs / 1_000
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds / 60 % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

private fun formatImportTime(epochMs: Long): String =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
