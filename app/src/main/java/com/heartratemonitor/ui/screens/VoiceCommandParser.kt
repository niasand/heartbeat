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

    private const val ALARM_SYSTEM_PROMPT = """你是一个闹钟指令解析器。用户会说出一段中文，你需要从中提取：
1. eventName: 闹钟用途（简洁，2-8个字，如"赶飞机"、"开会"、"接孩子"）
2. hour: 24小时制小时，0-23
3. minute: 分钟，0-59
4. dateOffsetDays: 今天为0，明天为1，后天为2；没有明确日期时为0

规则：
- "下午3:30" 返回 hour=15, minute=30
- "3点半" 返回 minute=30
- 如果没有明确用途，eventName 使用"智能闹钟"
- 事件名要去掉"帮我"、"我要"、"订闹钟"等无关词汇
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

    suspend fun parseAlarm(apiKey: String, text: String): VoiceAlarmResult? {
        if (text.isBlank()) return null

        parseAlarmLocally(text)?.let { return it }
        if (apiKey.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                val requestBody = gson.toJson(ChatRequest(
                    model = MODEL,
                    messages = listOf(
                        Message(role = "system", content = ALARM_SYSTEM_PROMPT),
                        Message(role = "user", content = text)
                    ),
                    temperature = 0.1,
                    maxTokens = 120
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
                val jsonStr = content
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val parsed = gson.fromJson(jsonStr, ParsedAlarmResult::class.java)
                val hour = parsed.hour?.coerceIn(0, 23) ?: return@withContext null
                val minute = parsed.minute?.coerceIn(0, 59) ?: 0
                val eventName = parsed.eventName?.ifBlank { parsed.purpose ?: "智能闹钟" }
                    ?: parsed.purpose?.ifBlank { "智能闹钟" }
                    ?: "智能闹钟"

                VoiceAlarmResult(
                    eventName = eventName,
                    hour = hour,
                    minute = minute,
                    dateOffsetDays = (parsed.dateOffsetDays ?: 0).coerceIn(0, 7)
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseAlarmLocally(text: String): VoiceAlarmResult? {
        val normalized = text.replace('：', ':')
        val timePattern = Regex("(今天|明天|后天)?\\s*(凌晨|早上|上午|中午|下午|晚上)?\\s*(\\d{1,2})(?:(?:[:点])(\\d{1,2})|点半|半)?")
        val match = timePattern.find(normalized) ?: return null

        val matchedText = match.value
        val hasTimeCue = matchedText.contains(":") ||
            matchedText.contains("点") ||
            matchedText.contains("半") ||
            match.groups[2]?.value != null ||
            normalized.contains("闹钟")
        if (!hasTimeCue) return null

        var hour = match.groups[3]?.value?.toIntOrNull() ?: return null
        val minute = when {
            matchedText.contains("半") -> 30
            else -> match.groups[4]?.value?.toIntOrNull() ?: 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null

        when (match.groups[2]?.value) {
            "下午", "晚上" -> if (hour in 1..11) hour += 12
            "中午" -> if (hour in 1..10) hour += 12
            "凌晨" -> if (hour == 12) hour = 0
        }

        val dateOffsetDays = when {
            match.groups[1]?.value == "后天" || normalized.contains("后天") -> 2
            match.groups[1]?.value == "明天" || normalized.contains("明天") -> 1
            else -> 0
        }

        return VoiceAlarmResult(
            eventName = extractAlarmPurpose(normalized, matchedText),
            hour = hour,
            minute = minute,
            dateOffsetDays = dateOffsetDays
        )
    }

    private fun extractAlarmPurpose(text: String, matchedTimeText: String): String {
        var purpose = text.replace(matchedTimeText, "")
        listOf("帮我", "请", "订", "定", "设置", "设", "一个", "的", "闹钟", "闹铃", "提醒我", "提醒", "我要", "我想", "我得", "一下")
            .forEach { purpose = purpose.replace(it, "") }
        purpose = purpose
            .replace(Regex("[，。,.!！?？\\s]+"), "")
            .replace(Regex("^(要去|得去|去)"), "")
            .trim()

        return purpose.ifBlank { "智能闹钟" }.take(12)
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

    data class ParsedAlarmResult(
        @SerializedName("eventName") val eventName: String?,
        @SerializedName("purpose") val purpose: String?,
        @SerializedName("hour") val hour: Int?,
        @SerializedName("minute") val minute: Int?,
        @SerializedName("dateOffsetDays") val dateOffsetDays: Int?
    )
}
