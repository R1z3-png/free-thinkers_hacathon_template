package org.freethinkers.transcriber.ui.transcript

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.freethinkers.transcriber.domain.model.ProcessingStatus
import org.freethinkers.transcriber.domain.model.Utterance
import org.freethinkers.transcriber.util.TimestampFormatter

@Composable
fun TranscriptScreen(
    state: TranscriptUiState,
    onPageChanged: (Int) -> Unit,
    onSaveJson: () -> Unit,
    showDemoResultButton: Boolean,
    onLoadDemoResult: () -> Unit,
    showCloseDemoButton: Boolean,
    onCloseDemoResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("\u0420\u0430\u0441\u0448\u0438\u0444\u0440\u043e\u0432\u043a\u0430", fontWeight = FontWeight.Bold)
            if (showCloseDemoButton) {
                IconButton(
                    onClick = onCloseDemoResult,
                    modifier = Modifier.semantics {
                        contentDescription = "\u0417\u0430\u043a\u0440\u044b\u0442\u044c \u0434\u0435\u043c\u043e-\u0440\u0430\u0441\u0448\u0438\u0444\u0440\u043e\u0432\u043a\u0443"
                    },
                ) {
                    Text("\u00d7", fontSize = 24.sp)
                }
            }
        }
        state.audioFileName?.let { fileName ->
            Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        when {
            state.transcript == null -> Text(transcriptPlaceholder(state))
            state.hasNoSpeech -> Text("\u0420\u0435\u0447\u044c \u043d\u0435 \u043e\u0431\u043d\u0430\u0440\u0443\u0436\u0435\u043d\u0430")
            else -> {
                state.warningMessage?.let { Text("\u041f\u0440\u0435\u0434\u0443\u043f\u0440\u0435\u0436\u0434\u0435\u043d\u0438\u0435: $it") }
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            state.currentPageUtterances.forEach { utterance ->
                                UtteranceView(utterance)
                            }
                        }
                    }
                }
                if (state.pageCount > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onPageChanged(state.currentPageIndex - 1) },
                            enabled = state.currentPageIndex > 0,
                        ) {
                            Text("\u041d\u0430\u0437\u0430\u0434")
                        }
                        Text(
                            text = "${state.currentPageIndex + 1} / ${state.pageCount}",
                            modifier = Modifier.weight(1f).padding(top = 14.dp),
                            textAlign = TextAlign.Center,
                        )
                        OutlinedButton(
                            onClick = { onPageChanged(state.currentPageIndex + 1) },
                            enabled = state.currentPageIndex < state.pageCount - 1,
                        ) {
                            Text("\u0414\u0430\u043b\u0435\u0435")
                        }
                    }
                }
            }
        }

        Button(onClick = onSaveJson, enabled = state.isExportAvailable) {
            Text("\u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c JSON")
        }
        if (showDemoResultButton) {
            OutlinedButton(onClick = onLoadDemoResult) {
                Text("\u041f\u043e\u043a\u0430\u0437\u0430\u0442\u044c \u0434\u0435\u043c\u043e-\u0440\u0430\u0441\u0448\u0438\u0444\u0440\u043e\u0432\u043a\u0443")
            }
        }
    }
}

@Composable
private fun UtteranceView(utterance: Utterance) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${speakerLabel(utterance.speakerId)} \u00b7 " +
                "${TimestampFormatter.format(utterance.startMs)} \u2014 ${TimestampFormatter.format(utterance.endMs)}",
            fontWeight = FontWeight.SemiBold,
        )
        Text(utterance.text)
    }
}

private fun speakerLabel(speakerId: String): String = when (speakerId) {
    "overlap" -> "\u041e\u0434\u043d\u043e\u0432\u0440\u0435\u043c\u0435\u043d\u043d\u0430\u044f \u0440\u0435\u0447\u044c"
    "unknown" -> "\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u044b\u0439 \u0433\u043e\u0432\u043e\u0440\u044f\u0449\u0438\u0439"
    else -> speakerId
}

private fun transcriptPlaceholder(state: TranscriptUiState): String = when {
    state.processingStatus == null -> "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u0432\u044b\u0431\u0435\u0440\u0438\u0442\u0435 MP3 \u043d\u0430 \u0432\u043a\u043b\u0430\u0434\u043a\u0435 \u00ab\u0410\u0443\u0434\u0438\u043e\u00bb."
    state.processingStatus == ProcessingStatus.PROCESSING -> "\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430 \u0435\u0449\u0451 \u043d\u0435 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0430."
    state.processingStatus == ProcessingStatus.ERROR -> state.errorMessage
        ?: "\u0420\u0430\u0441\u0448\u0438\u0444\u0440\u043e\u0432\u043a\u0430 \u043d\u0435 \u0433\u043e\u0442\u043e\u0432\u0430: \u043e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430 \u0437\u0430\u0432\u0435\u0440\u0448\u0438\u043b\u0430\u0441\u044c \u043e\u0448\u0438\u0431\u043a\u043e\u0439."
    else -> "\u0417\u0430\u043f\u0443\u0441\u0442\u0438\u0442\u0435 \u043e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0443 \u043d\u0430 \u0432\u043a\u043b\u0430\u0434\u043a\u0435 \u00ab\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430\u00bb, \u0447\u0442\u043e\u0431\u044b \u0443\u0432\u0438\u0434\u0435\u0442\u044c \u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442."
}
