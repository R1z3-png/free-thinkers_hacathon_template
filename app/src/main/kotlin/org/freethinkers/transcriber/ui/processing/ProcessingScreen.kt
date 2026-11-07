package org.freethinkers.transcriber.ui.processing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.freethinkers.transcriber.domain.model.ProcessingStatus
import org.freethinkers.transcriber.domain.model.RecognitionLanguage

@Composable
fun ProcessingScreen(
    state: ProcessingUiState,
    onLanguageSelected: (String) -> Unit,
    onStartProcessing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var languageMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val selectedLanguage = RecognitionLanguage.fromTag(state.languageTag)

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Обработка")
        Text("Файл: ${state.audioFileName ?: "не выбран"}")

        Text("Язык распознавания")
        Box {
            OutlinedButton(
                onClick = { languageMenuExpanded = true },
                enabled = state.hasAudioFile && !state.isBusy,
            ) {
                Text(selectedLanguage?.displayName ?: "Select language")
            }
            DropdownMenu(
                expanded = languageMenuExpanded,
                onDismissRequest = { languageMenuExpanded = false },
            ) {
                RecognitionLanguage.entries.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.displayName) },
                        onClick = {
                            languageMenuExpanded = false
                            onLanguageSelected(language.tag)
                        },
                    )
                }
            }
        }

        Text(
            "Локальные модели (STT, диаризация и LLM): " +
                if (state.modelFilesInstalled) "установлены" else "не установлены",
        )
        Text("Статус показывает наличие файлов моделей в приложении. В оперативную память LLM будет загружаться на время постобработки.")
        if (!state.modelFilesInstalled) {
            Text("Установка моделей станет доступна после подключения ML-пайплайна.")
        }

        Text(processingStatusText(state))
        state.warningMessage?.let { Text("Предупреждение: $it") }

        Button(
            onClick = onStartProcessing,
            enabled = state.hasAudioFile && state.modelFilesInstalled && !state.isBusy,
        ) {
            Text(if (state.isBusy) "Обработка…" else "Начать обработку")
        }
    }
}

private fun processingStatusText(state: ProcessingUiState): String = when (state.processingStatus) {
    ProcessingStatus.PROCESSING -> "Выполняется локальная обработка…"
    ProcessingStatus.COMPLETED -> "Обработка завершена."
    ProcessingStatus.ERROR -> state.errorMessage ?: "Обработка завершилась ошибкой."
    ProcessingStatus.READY -> if (state.hasAudioFile) "Файл готов к обработке." else "Сначала выберите MP3 на вкладке «Аудио»."
    null -> if (state.hasAudioFile) "Файл готов к обработке." else "Сначала выберите MP3 на вкладке «Аудио»."
}
