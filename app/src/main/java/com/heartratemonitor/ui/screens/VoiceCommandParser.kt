package com.heartratemonitor.ui.screens

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Parses Chinese natural-language voice commands into structured timer data
 * using Silicon Flow's DeepSeek-V2.5 API.
 *
 * Example: "我要煎个牛排，帮我倒计时7分钟" → VoiceInputResult(eventName="煎牛排", minutes=7, seconds=0)
 */
object VoiceCommandParser {

    private const val API_BASE = "https://api.siliconflow.cn/v1/chat/completions"
    private const val MODEL = "deepseek-ai/DeepSeek-V2.5"

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT = """你是一个计时指令解析器。用户会说出一段语音文字，你需要从中提取：
1. eventName: 计时事件名称（简洁，2-6个字，如"煎牛排"、"冥想"、"拉伸"）
2. minutes: 分钟数（整数）
3. seconds: 秒数（整数，默认0）

规则：
- 如果用户只说秒数，minutes 为 0
- 如果用户说"半小时"，minutes 为 30
- 如果用户说"一个半小时"，minutes 为 90
- 如果用户没有明确说事件名，使用"语音计时"
- 事件名要简洁，去掉"我要"、"帮我"等无关词汇
- 只返回 JSON，不要其他文字"""

    /**
     * Parse voice text into a VoiceInputResult using LLM API.
     * Returns null if parsing fails.
     */
    suspend fun parse(apiKey: String, text: String): VoiceInputResult? {
        if (apiKey.isBlank() || text.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                val requestBody = gson.toJson(ChatRequest(
                    model = MODEL,
                    messages = listOf(
                        Message(role = "system", content = SYSTEM_PROMPT),
                        Message(role = "user", content = text)
                    ),
                    temperature = 0.1,
                    maxTokens = 100
                ))

                val request = Request.Builder()
                    .url(API_BASE)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null

                if (!response.isSuccessful) return@withContext null

                val chatResponse = gson.fromJson(body, ChatResponse::class.java)
                val content = chatResponse.choices.firstOrNull()?.message?.content ?: return@withContext null

                // Extract JSON from response (handle markdown code blocks)
                val jsonStr = content
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val parsed = gson.fromJson(jsonStr, ParsedResult::class.java)
                VoiceInputResult(
                    eventName = parsed.eventName?.ifBlank { "语音计时" } ?: "语音计时",
                    minutes = parsed.minutes?.coerceIn(0, 999) ?: 0,
                    seconds = parsed.seconds?.coerceIn(0, 59) ?: 0
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    // ── API data classes ───────────────────────────────────────────────

    data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double,
        val maxTokens: Int
    )

    data class Message(
        val role: String,
        val content: String
    )

    data class ChatResponse(
        val choices: List<Choice>
    )

    data class Choice(
        val message: Message
    )

    data class ParsedResult(
        @SerializedName("eventName") val eventName: String?,
        @SerializedName("minutes") val minutes: Int?,
        @SerializedName("seconds") val seconds: Int?
    )
}
