package com.heartratemonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heartratemonitor.R
import kotlinx.coroutines.launch

/**
 * Result of AI-parsed timer command.
 */
data class VoiceInputResult(
    val eventName: String,
    val minutes: Int,
    val seconds: Int
)

/** Dialog phases. */
private enum class DialogPhase {
    INPUT,          // User is typing
    PARSING,        // Calling LLM API
    PARSE_FAILED    // Parse failed, user can retry
}

/**
 * Text input dialog that sends user input to Silicon Flow DeepSeek-V2.5
 * for natural-language timer command parsing.
 */
@Composable
fun VoiceInputDialog(
    apiKey: String,
    onDismiss: () -> Unit,
    onResult: (VoiceInputResult?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var phase by remember { mutableStateOf(DialogPhase.INPUT) }
    var inputText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun parseAndStart() {
        val text = inputText.trim()
        if (text.isBlank()) return
        if (apiKey.isBlank()) {
            errorMessage = "请先在设置中配置 AI 语音解析的 API Key"
            return
        }
        phase = DialogPhase.PARSING
        errorMessage = null
        scope.launch {
            val result = VoiceCommandParser.parse(apiKey, text)
            if (result != null) {
                onResult(result)
            } else {
                phase = DialogPhase.PARSE_FAILED
                errorMessage = context.getString(R.string.voice_error_parse_failed)
            }
        }
    }

    // Auto-focus keyboard when dialog opens
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = { if (phase != DialogPhase.PARSING) onDismiss() },
        title = {
            Text(
                text = "智能计时",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "输入你要做的事和时间，例如：\n煎牛排 7分钟\n冥想 5分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Text input field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("煎牛排 7分钟") },
                    singleLine = true,
                    enabled = phase != DialogPhase.PARSING,
                    shape = RoundedCornerShape(12.dp)
                )

                // Parsing progress
                if (phase == DialogPhase.PARSING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "正在解析…",
                            style = MaterialTheme.typography.bodyMedium,
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
        confirmButton = {
            TextButton(
                onClick = { parseAndStart() },
                enabled = inputText.isNotBlank() && phase == DialogPhase.INPUT
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (phase == DialogPhase.PARSE_FAILED) "重试" else "开始")
            }
        },
        dismissButton = {
            if (phase != DialogPhase.PARSING) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.voice_cancel))
                }
            }
        }
    )
}
