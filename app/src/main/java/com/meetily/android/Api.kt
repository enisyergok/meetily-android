package com.meetily.android

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class SummaryResult(
    val summary: String,
    val topics: List<String>,
    val actions: List<ActionItem>,
    val decisions: List<String>
)

object Api {

    // NVIDIA anahtari uygulamaya gomulu (kullanicinin sagladigi).
    // Guvenlik notu: repo PUBLIC ise bu gorunur olur; gerekirse NVIDIA panelinden yenile.
    private const val NVIDIA_KEY =
        "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"
    private const val NVIDIA_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
    private const val GROQ_TRANSCRIBE_URL = "https://api.groq.com/openai/v1/audio/transcriptions"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // ── Ses -> metin (Groq Whisper). groqKey bos ise acik hata doner, sahte veri uretmez. ──
    fun transcribe(file: File, groqKey: String, language: String = "tr"): Result<String> {
        if (groqKey.isBlank())
            return Result.failure(Exception("Groq API anahtari yok. Ayarlar'dan ekle (ucretsiz: console.groq.com)."))
        if (!file.exists() || file.length() == 0L)
            return Result.failure(Exception("Ses dosyasi bos veya bulunamadi."))
        return try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaTypeOrNull()))
                .addFormDataPart("model", "whisper-large-v3")
                .addFormDataPart("language", language)
                .addFormDataPart("response_format", "json").addFormDataPart("temperature", "0").addFormDataPart("temperature", "0")
                .build()
            val req = Request.Builder()
                .url(GROQ_TRANSCRIBE_URL)
                .addHeader("Authorization", "Bearer $groqKey")
                .post(body)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return Result.failure(Exception("Whisper hatasi ${resp.code}: ${resp.body?.string()}"))
                val text = JSONObject(resp.body?.string() ?: "{}").optString("text", "").trim()
                if (text.isEmpty()) Result.failure(Exception("Whisper bos metin dondurdu.")) else Result.success(text)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Ozet + basliklar + aksiyonlar + kararlar (NVIDIA, gomulu anahtar). ──
    fun summarize(transcript: String, model: String): Result<SummaryResult> {
        val system = """Sen profesyonel bir toplanti asistanisin. Verilen Turkce transkripti analiz et ve YALNIZCA gecerli JSON dondur, baska aciklama ekleme:
{"summary":"2-3 cumle ozet","topics":["baslik1","baslik2"],"actions":[{"task":"gorev","assignee":"kisi","deadline":"tarih veya bos","priority":"KRITIK|YUKSEK|ORTA"}],"decisions":["karar1"]}
Eger bir alan yoksa bos birak ama JSON yapisini koru."""
        return chat(system, "Transkript:\n$transcript", model).map { raw -> parseSummary(raw) }
    }

    // ── AI sohbet: transkript baglaminda soru-cevap (NVIDIA). ──
    fun ask(question: String, transcript: String, model: String): Result<String> {
        val system = "Sen Meetily adli bir toplanti asistanisin. Asagidaki transkripte dayanarak kisa, net, Turkce yanitla. Transkritte olmayan bilgi uydurma."
        val user = "TOPLANTI TRANSKRIPTI:\n$transcript\n\nSORU: $question"
        return chat(system, user, model)
    }

    private fun chat(system: String, user: String, model: String): Result<String> {
        return try {
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user))
            val payload = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.3)
                put("max_tokens", 2048)
                put("stream", false)
            }
            val req = Request.Builder()
                .url(NVIDIA_URL)
                .addHeader("Authorization", "Bearer $NVIDIA_KEY")
                .addHeader("Accept", "application/json")
                .post(payload.toString().toRequestBody(JSON))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return Result.failure(Exception("NVIDIA hatasi ${resp.code}: ${resp.body?.string()}"))
                val obj = JSONObject(resp.body?.string() ?: "{}")
                val content = obj.optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content", "") ?: ""
                if (content.isBlank()) Result.failure(Exception("NVIDIA bos yanit dondurdu.")) else Result.success(content)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun parseSummary(raw: String): SummaryResult {
        val json = extractJson(raw)
        return try {
            val o = JSONObject(json)
            val topics = o.optJSONArray("topics")?.let { (0 until it.length()).map { i -> it.getString(i) } } ?: emptyList()
            val decisions = o.optJSONArray("decisions")?.let { (0 until it.length()).map { i -> it.getString(i) } } ?: emptyList()
            val actions = o.optJSONArray("actions")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val a = arr.getJSONObject(i)
                    ActionItem(
                        task = a.optString("task", ""),
                        assignee = a.optString("assignee", "Belirtilmemis"),
                        deadline = a.optString("deadline", ""),
                        priority = a.optString("priority", "ORTA")
                    )
                }
            } ?: emptyList()
            SummaryResult(o.optString("summary", raw), topics, actions, decisions)
        } catch (e: Exception) {
            SummaryResult(raw, emptyList(), emptyList(), emptyList())
        }
    }

    private fun extractJson(text: String): String {
        val block = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)
        if (block != null) return block.groupValues[1].trim()
        val first = text.indexOfFirst { it == '{' || it == '[' }
        val last = text.indexOfLast { it == '}' || it == ']' }
        return if (first >= 0 && last > first) text.substring(first, last + 1) else text
    }

    fun toJsonArray(list: List<String>): String = JSONArray().apply { list.forEach { put(it) } }.toString()
    fun toJsonArrayActions(list: List<ActionItem>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
}

object ApiTimed {
    fun transcribe(file: File, groqKey: String, language: String = "tr"): Result<String> {
        if (groqKey.isBlank()) return Result.failure(Exception("Groq anahtari yok."))
        if (!file.exists() || file.length() == 0L) return Result.failure(Exception("Dosya bos."))
        return try {
            val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaTypeOrNull()))
                .addFormDataPart("model", "whisper-large-v3")
                .addFormDataPart("language", language)
                .addFormDataPart("response_format", "verbose_json").addFormDataPart("temperature", "0")
                .build()
            val req = okhttp3.Request.Builder().url("https://api.groq.com/openai/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $groqKey").post(body).build()
            val client = okhttp3.OkHttpClient.Builder().readTimeout(180, java.util.concurrent.TimeUnit.SECONDS).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return Result.failure(Exception("Whisper ${r.code}"))
                val o = org.json.JSONObject(r.body?.string() ?: "{}")
                val segs = o.optJSONArray("segments")
                if (segs == null || segs.length() == 0) return Result.success(o.optString("text", ""))
                val sb = StringBuilder()
                for (i in 0 until segs.length()) {
                    val s = segs.getJSONObject(i)
                    val t = s.optDouble("start", 0.0).toInt()
                    sb.append(String.format("[%02d:%02d] %s", t / 60, t % 60, s.optString("text", "").trim())).append("\n")
                }
                Result.success(sb.toString().trim())
            }
        } catch (e: Exception) { Result.failure(e) }
    }
}

object ApiTimed {
    fun transcribe(file: File, groqKey: String, language: String = "tr"): Result<String> {
        if (groqKey.isBlank()) return Result.failure(Exception("Groq anahtari yok."))
        if (!file.exists() || file.length() == 0L) return Result.failure(Exception("Dosya bos."))
        return try {
            val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaTypeOrNull()))
                .addFormDataPart("model", "whisper-large-v3")
                .addFormDataPart("language", language)
                .addFormDataPart("response_format", "verbose_json").addFormDataPart("temperature", "0")
                .build()
            val req = okhttp3.Request.Builder().url("https://api.groq.com/openai/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $groqKey").post(body).build()
            val client = okhttp3.OkHttpClient.Builder().readTimeout(180, java.util.concurrent.TimeUnit.SECONDS).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return Result.failure(Exception("Whisper ${r.code}"))
                val o = org.json.JSONObject(r.body?.string() ?: "{}")
                val segs = o.optJSONArray("segments")
                if (segs == null || segs.length() == 0) return Result.success(o.optString("text", ""))
                val sb = StringBuilder()
                for (i in 0 until segs.length()) {
                    val s = segs.getJSONObject(i)
                    val t = s.optDouble("start", 0.0).toInt()
                    sb.append(String.format("[%02d:%02d] %s", t / 60, t % 60, s.optString("text", "").trim())).append("\n")
                }
                Result.success(sb.toString().trim())
            }
        } catch (e: Exception) { Result.failure(e) }
    }
}
