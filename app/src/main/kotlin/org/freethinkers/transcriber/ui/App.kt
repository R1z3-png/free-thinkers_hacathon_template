package org.freethinkers.transcriber.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.freethinkers.transcriber.data.audio.AndroidAudioMetadataReader
import org.freethinkers.transcriber.data.audio.AudioImportException
import org.freethinkers.transcriber.data.audio.rememberAndroidAudioPicker
import org.freethinkers.transcriber.data.export.AndroidContentResolverJsonSaver
import org.freethinkers.transcriber.data.export.TranscriptJsonSerializer
import org.freethinkers.transcriber.data.models.LocalModelRepository
import org.freethinkers.transcriber.data.processing.LocalTranscriptionEngine
import org.freethinkers.transcriber.data.session.InMemoryAudioSessionRepository
import org.freethinkers.transcriber.domain.model.ProcessingStatus
import org.freethinkers.transcriber.domain.usecase.ClearAudioSession
import org.freethinkers.transcriber.domain.usecase.ExportTranscript
import org.freethinkers.transcriber.domain.usecase.ImportAudio
import org.freethinkers.transcriber.domain.usecase.ProcessAudio
import org.freethinkers.transcriber.domain.usecase.SelectRecognitionLanguage
import org.freethinkers.transcriber.ui.audio.AudioScreen
import org.freethinkers.transcriber.ui.audio.AudioUiState
import org.freethinkers.transcriber.ui.navigation.AppTab
import org.freethinkers.transcriber.ui.processing.ProcessingScreen
import org.freethinkers.transcriber.ui.processing.ProcessingUiState
import org.freethinkers.transcriber.ui.transcript.TranscriptScreen
import org.freethinkers.transcriber.ui.transcript.TranscriptUiState

@Composable
fun App() {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Audio) }
    val sessionRepository = remember { InMemoryAudioSessionRepository() }
    val session by sessionRepository.currentSession.collectAsState()
    var transcriptPage by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(session) { transcriptPage = 0 }
    val importAudio = remember(sessionRepository) { ImportAudio(sessionRepository) }
    val clearAudioSession = remember(sessionRepository) { ClearAudioSession(sessionRepository) }
    val modelRepository = remember { LocalModelRepository() }
    val transcriptionEngine = remember { LocalTranscriptionEngine() }
    val processAudio = remember(sessionRepository, modelRepository, transcriptionEngine) {
        ProcessAudio(sessionRepository, modelRepository, transcriptionEngine)
    }
    val selectRecognitionLanguage = remember(sessionRepository) {
        SelectRecognitionLanguage(sessionRepository)
    }
    var audioErrorMessage by remember { mutableStateOf<String?>(null) }
    val audioUiState = AudioUiState(
        audioFile = session?.audioFile,
        errorMessage = audioErrorMessage,
    )
    val processingUiState = ProcessingUiState(
        audioFileName = session?.audioFile?.fileName,
        languageTag = session?.languageTag ?: "ru",
        modelFilesInstalled = modelRepository.areRequiredModelsInstalled(),
        processingStatus = session?.processingStatus,
        warningMessage = session?.warningMessage,
        errorMessage = session?.errorMessage,
    )
    val processing = session?.processingStatus == ProcessingStatus.PROCESSING
    val context = LocalContext.current.applicationContext
    val metadataReader = remember(context) { AndroidAudioMetadataReader(context) }
    val jsonSaver = remember(context) { AndroidContentResolverJsonSaver(context.contentResolver) }
    val exportTranscript = remember { ExportTranscript() }
    val coroutineScope = rememberCoroutineScope()
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { destination ->
        val json = pendingExportJson
        pendingExportJson = null
        if (destination != null && json != null) {
            coroutineScope.launch {
                val saved = withContext(Dispatchers.IO) { jsonSaver.saveJson(destination, json) }
                Toast.makeText(
                    context,
                    if (saved) "\u004a\u0053\u004f\u004e\u0020\u0441\u043e\u0445\u0440\u0430\u043d\u0451\u043d" else "\u041d\u0435\u0020\u0443\u0434\u0430\u043b\u043e\u0441\u044c\u0020\u0441\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u044c\u0020\u004a\u0053\u004f\u004e",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val chooseAudio = rememberAndroidAudioPicker { uri: Uri ->
        audioErrorMessage = null
        coroutineScope.launch {
            try {
                val audioFile = withContext(Dispatchers.IO) {
                    metadataReader.readAndValidate(uri.toString())
                }
                importAudio.execute(audioFile)
                audioErrorMessage = null
            } catch (error: AudioImportException) {
                audioErrorMessage = error.message
            } catch (_: Exception) {
                audioErrorMessage = "Не удалось прочитать файл. Выберите другой MP3."
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { if (!processing) selectedTab = tab },
                        enabled = !processing,
                        icon = { Text(tab.title.take(1)) },
                        label = { Text(tab.title) },
                    )
                }
            }
        },
    ) { contentPadding ->
        when (selectedTab) {
            AppTab.Audio -> AudioScreen(
                state = audioUiState,
                onChooseAudio = chooseAudio,
                onClearSession = {
                    clearAudioSession.execute()
                    audioErrorMessage = null
                },
                modifier = Modifier.padding(contentPadding),
            )
            AppTab.Processing -> ProcessingScreen(
                state = processingUiState,
                onLanguageSelected = { languageTag ->
                    selectRecognitionLanguage.execute(languageTag)
                },
                onStartProcessing = {
                    coroutineScope.launch { processAudio.execute() }
                },
                modifier = Modifier.padding(contentPadding),
            )
            AppTab.Transcript -> TranscriptScreen(
                state = TranscriptUiState.from(session, transcriptPage),
                onPageChanged = { page -> transcriptPage = page.coerceAtLeast(0) },
                onSaveJson = {
                    val current = session
                    val transcript = current?.transcript
                    if (current?.processingStatus == ProcessingStatus.COMPLETED && transcript != null) {
                        pendingExportJson = exportTranscript.execute(transcript)
                        saveJsonLauncher.launch(exportTranscript.suggestedFileName(current.audioFile.fileName))
                    }
                },
                modifier = Modifier.padding(contentPadding),
            )
        }
    }
}
