package com.heartratemonitor.ui.screens

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heartratemonitor.R
import kotlinx.coroutines.launch

/**
 * Result of voice input parsing.
 */
data class VoiceInputResult(
    val eventName: String,
    val minutes: Int,
    val seconds: Int
)

/** States for the voice input dialog flow. */
private enum class DialogPhase {
    READY,           // Waiting for user to tap mic
    LISTENING,       // SpeechRecognizer is active
    PARSING,         // Calling LLM API (auto-triggered after recognition)
    PARSE_FAILED     // LLM parse failed, user can retry
}

/**
 * Voice input dialog that uses Android's built-in SpeechRecognizer
 * and Silicon Flow's DeepSeek-V2.5 to parse timer commands.
 */
@Composable
fun VoiceInputDialog(
    apiKey: String,
    onDismiss: () -> Unit,
    onResult: (VoiceInputResult?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(DialogPhase.READY) }
    var recognizedText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    val speechRecognizer = remember {
        if (isAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                phase = DialogPhase.LISTENING
                errorMessage = null
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // Don't change phase here — onResults or onError will follow
            }

            override fun onError(error: Int) {
                phase = DialogPhase.READY
                errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.voice_error_no_match)
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.voice_error_no_speech)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.voice_error_permission)
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_SERVER -> "网络错误，请检查网络连接"
                    else -> "语音识别错误 ($error)"
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                    errorMessage = null
                    // Auto-parse: skip confirmation, call LLM directly
                    if (apiKey.isBlank()) {
                        phase = DialogPhase.PARSE_FAILED
                        errorMessage = "请先在设置中配置 AI 语音解析的 API Key"
                        return
                    }
                    phase = DialogPhase.PARSING
                    scope.launch {
                        val result = VoiceCommandParser.parse(apiKey, recognizedText)
                        if (result != null) {
                            onResult(result)
                        } else {
                            phase = DialogPhase.PARSE_FAILED
                            errorMessage = context.getString(R.string.voice_error_parse_failed)
                        }
                    }
                } else {
                    phase = DialogPhase.READY
                    errorMessage = context.getString(R.string.voice_error_no_match)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    // Clean up SpeechRecognizer on dismiss
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
    }

    fun startListening() {
        if (speechRecognizer == null) return
        recognizedText = ""
        errorMessage = null
        speechRecognizer.setRecognitionListener(recognitionListener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要倒计时的事件和时间")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    AlertDialog(
        onDismissRequest = {
            speechRecognizer?.cancel()
            onDismiss()
        },
        title = {
            Text(
                text = stringResource(R.string.voice_input),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hint
                Text(
                    text = stringResource(R.string.voice_input_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Microphone button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Listening indicator ring
                    if (phase == DialogPhase.LISTENING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(80.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Parsing indicator ring
                    if (phase == DialogPhase.PARSING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(80.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    FilledIconButton(
                        onClick = {
                            when (phase) {
                                DialogPhase.LISTENING -> stopListening()
                                DialogPhase.READY, DialogPhase.PARSE_FAILED -> startListening()
                                else -> {}
                            }
                        },
                        modifier = Modifier.size(64.dp),
                        enabled = phase != DialogPhase.PARSING,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = when (phase) {
                                DialogPhase.LISTENING -> MaterialTheme.colorScheme.error
                                DialogPhase.PARSING -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        when (phase) {
                            DialogPhase.LISTENING -> Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = stringResource(R.string.voice_listening),
                                modifier = Modifier.size(32.dp)
                            )
                            DialogPhase.PARSING -> CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.onTertiary,
                                strokeWidth = 3.dp
                            )
                            else -> Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = stringResource(R.string.voice_input),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Status text
                when (phase) {
                    DialogPhase.LISTENING -> Text(
                        text = stringResource(R.string.voice_listening),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    DialogPhase.PARSING -> Text(
                        text = "正在解析…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    else -> {}
                }

                // Recognized text display
                if (recognizedText.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = recognizedText,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Error message
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                when (phase) {
                    DialogPhase.PARSING -> {} // Ignore taps during parsing
                    DialogPhase.PARSE_FAILED -> startListening()
                    else -> {
                        speechRecognizer?.cancel()
                        onDismiss()
                    }
                }
            }) {
                Text(
                    when {
                        phase == DialogPhase.PARSING -> ""
                        phase == DialogPhase.PARSE_FAILED -> stringResource(R.string.voice_retry)
                        else -> stringResource(R.string.voice_cancel)
                    }
                )
            }
        }
    )
}

/**
 * Check if the device supports speech recognition.
 */
@Composable
fun isSpeechRecognitionAvailable(): Boolean {
    val context = LocalContext.current
    return remember { SpeechRecognizer.isRecognitionAvailable(context) }
}
