package org.freethinkers.transcriber.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.freethinkers.transcriber.data.audio.AndroidAudioMetadataReader
import org.freethinkers.transcriber.data.audio.AudioImportException
import org.freethinkers.transcriber.data.audio.rememberAndroidAudioPicker
import org.freethinkers.transcriber.data.export.AndroidContentResolverJsonSaver
import org.freethinkers.transcriber.data.export.TranscriptJsonSerializer
import org.freethinkers.transcriber.data.models.LocalModelRepository
import org.freethinkers.transcriber.data.processing.createAndroidAudioProcessingPipeline
import org.freethinkers.transcriber.data.processing.createDemoTranscriptSource
import org.freethinkers.transcriber.data.processing.PipelineTranscriptionEngine
import org.freethinkers.transcriber.data.session.InMemoryAudioSessionRepository
import org.freethinkers.transcriber.domain.model.ProcessingStatus
import org.freethinkers.transcriber.domain.model.AudioFile
import org.freethinkers.transcriber.domain.model.AudioSession
import org.freethinkers.transcriber.domain.usecase.ClearAudioSession
import org.freethinkers.transcriber.domain.usecase.ExportTranscript
import org.freethinkers.transcriber.domain.usecase.ImportAudio
import org.freethinkers.transcriber.domain.usecase.ProcessAudio
import org.freethinkers.transcriber.domain.usecase.RetryPostProcessing
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
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current.applicationContext
    val sessionRepository = remember { InMemoryAudioSessionRepository() }
    val session by sessionRepository.currentSession.collectAsState()
    var transcriptPage by rememberSaveable { mutableStateOf(0) }
    var isDemoTranscriptActive by remember { mutableStateOf(false) }
    var sessionBeforeDemo by remember { mutableStateOf<AudioSession?>(null) }
    LaunchedEffect(session) { transcriptPage = 0 }
    val importAudio = remember(sessionRepository) { ImportAudio(sessionRepository) }
    val clearAudioSession = remember(sessionRepository) { ClearAudioSession(sessionRepository) }
    val modelRepository = remember(context) { LocalModelRepository(context) }
    var modelFilesInstalled by remember(context) { mutableStateOf(false) }
    var modelSetupMessage by remember(context) { mutableStateOf<String?>(null) }
    var processingElapsedMs by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(modelRepository) {
        val installError = runCatching { modelRepository.installRequiredModels() }.exceptionOrNull()
        modelFilesInstalled = modelRepository.areRequiredModelsInstalled()
        modelSetupMessage = when {
            installError != null -> installError.message ?: "Не удалось подготовить файлы моделей."
            !modelFilesInstalled -> "Не удалось загрузить нативные библиотеки обработки."
            else -> null
        }
    }
    val transcriptionEngine = remember(context) {
        PipelineTranscriptionEngine(createAndroidAudioProcessingPipeline(context))
    }
    val processAudio = remember(sessionRepository, modelRepository, transcriptionEngine) {
        ProcessAudio(sessionRepository, modelRepository, transcriptionEngine)
    }
    val retryPostProcessing = remember(sessionRepository, transcriptionEngine) {
        RetryPostProcessing(sessionRepository, transcriptionEngine)
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
        modelFilesInstalled = modelFilesInstalled,
        modelSetupMessage = modelSetupMessage,
        processingStatus = session?.processingStatus,
        elapsedMs = processingElapsedMs,
        warningMessage = session?.warningMessage,
        errorMessage = session?.errorMessage,
    )
    val processing = session?.processingStatus == ProcessingStatus.PROCESSING
    val activity = LocalContext.current.findActivity()
    val metadataReader = remember(context) { AndroidAudioMetadataReader(context) }
    val demoTranscriptSource = remember(context) { createDemoTranscriptSource(context) }
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
                processingElapsedMs = 0L
                isDemoTranscriptActive = false
                sessionBeforeDemo = null
                audioErrorMessage = null
            } catch (error: AudioImportException) {
                audioErrorMessage = error.message
            } catch (_: Exception) {
                audioErrorMessage = "Не удалось прочитать файл. Выберите другой MP3."
            }
        }
    }

    BackHandler(enabled = !showExitConfirmation) {
        showExitConfirmation = true
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("\u0412\u044b\u0439\u0442\u0438 \u0438\u0437 \u043f\u0440\u0438\u043b\u043e\u0436\u0435\u043d\u0438\u044f?") },
            text = { Text(exitWarningMessage(session)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmation = false
                        activity?.finish()
                    },
                ) {
                    Text("\u0412\u044b\u0439\u0442\u0438")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("\u041e\u0441\u0442\u0430\u0442\u044c\u0441\u044f")
                }
            },
        )
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
                    isDemoTranscriptActive = false
                    sessionBeforeDemo = null
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
                    processingElapsedMs = 0L
                    coroutineScope.launch {
                        val startedAt = SystemClock.elapsedRealtime()
                        val timer = launch {
                            while (isActive) {
                                processingElapsedMs = SystemClock.elapsedRealtime() - startedAt
                                delay(1_000L)
                            }
                        }
                        try {
                            processAudio.execute()
                        } finally {
                            processingElapsedMs = SystemClock.elapsedRealtime() - startedAt
                            timer.cancel()
                        }
                    }
                },
                modifier = Modifier.padding(contentPadding),
            )
            AppTab.Transcript -> TranscriptScreen(
                state = TranscriptUiState.from(session, transcriptPage),
                onPageChanged = { page -> transcriptPage = page.coerceAtLeast(0) },
                onRetryPostProcessing = { coroutineScope.launch { retryPostProcessing.execute() } },
                showDemoResultButton = demoTranscriptSource.isAvailable && !isDemoTranscriptActive,
                onLoadDemoResult = {
                    try {
                        val transcript = demoTranscriptSource.load()
                        val currentSession = session
                        val audioFile = currentSession?.audioFile ?: AudioFile(
                            contentUri = "demo://transcript-fixture",
                            fileName = "demo.mp3",
                            durationMs = transcript.utterances.maxOfOrNull { it.endMs } ?: 0L,
                            importedAtEpochMs = System.currentTimeMillis(),
                        )
                        sessionBeforeDemo = currentSession
                        sessionRepository.replaceSession(
                            AudioSession(
                                audioFile = audioFile,
                                languageTag = currentSession?.languageTag ?: "ru",
                                processingStatus = ProcessingStatus.COMPLETED,
                                transcript = transcript,
                                warningMessage = "\u0414\u0435\u043c\u043e: \u044d\u0442\u043e\u0442 \u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442 \u0437\u0430\u0433\u0440\u0443\u0436\u0435\u043d \u0438\u0437 JSON-\u043f\u0440\u0438\u043c\u0435\u0440\u0430, \u0430\u0443\u0434\u0438\u043e \u043d\u0435 \u043e\u0431\u0440\u0430\u0431\u0430\u0442\u044b\u0432\u0430\u043b\u043e\u0441\u044c.",
                            ),
                        )
                        isDemoTranscriptActive = true
                        transcriptPage = 0
                    } catch (error: Exception) {
                        Toast.makeText(
                            context,
                            error.message ?: "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c JSON-\u043f\u0440\u0438\u043c\u0435\u0440",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                showCloseDemoButton = isDemoTranscriptActive,
                onCloseDemoResult = {
                    if (isDemoTranscriptActive) {
                        val previousSession = sessionBeforeDemo
                        if (previousSession == null) {
                            clearAudioSession.execute()
                        } else {
                            sessionRepository.replaceSession(previousSession)
                        }
                        isDemoTranscriptActive = false
                        sessionBeforeDemo = null
                    }
                },
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

private fun exitWarningMessage(session: org.freethinkers.transcriber.domain.model.AudioSession?): String = when {
    session?.transcript != null ->
        "\u041f\u0440\u0438 \u0432\u044b\u0445\u043e\u0434\u0435 \u0442\u0435\u043a\u0443\u0449\u0430\u044f \u0440\u0430\u0441\u0448\u0438\u0444\u0440\u043e\u0432\u043a\u0430 \u0431\u0443\u0434\u0435\u0442 \u043f\u043e\u0442\u0435\u0440\u044f\u043d\u0430. \u0421\u043e\u0445\u0440\u0430\u043d\u0438\u0442\u0435 \u0435\u0451 \u0432 JSON, \u0435\u0441\u043b\u0438 \u0445\u043e\u0442\u0438\u0442\u0435 \u043e\u0441\u0442\u0430\u0432\u0438\u0442\u044c \u043a\u043e\u043f\u0438\u044e."
    session != null ->
        "\u041f\u0440\u0438 \u0432\u044b\u0445\u043e\u0434\u0435 \u0442\u0435\u043a\u0443\u0449\u0430\u044f \u0441\u0435\u0441\u0441\u0438\u044f \u0431\u0443\u0434\u0435\u0442 \u043f\u043e\u0442\u0435\u0440\u044f\u043d\u0430."
    else -> "\u0412\u044b \u0443\u0432\u0435\u0440\u0435\u043d\u044b, \u0447\u0442\u043e \u0445\u043e\u0442\u0438\u0442\u0435 \u0432\u044b\u0439\u0442\u0438?"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
