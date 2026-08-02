package com.meetily.android

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Meeting(
    val id: Long,
    var title: String,
    var audioPath: String,
    var durationMs: Long = 0L,
    var createdAt: Long = System.currentTimeMillis(),
    var status: String = STATUS_DONE,
    var transcript: String = "",
    var summary: String = "",
    var topicsJson: String = "[]",
    var actionsJson: String = "[]",
    var decisionsJson: String = "[]"
) {
    fun topics(): List<String> = try {
        val a = JSONArray(topicsJson); (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) { emptyList() }

    fun actions(): List<ActionItem> = try {
        val a = JSONArray(actionsJson); (0 until a.length()).map { ActionItem.fromJson(a.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    fun decisions(): List<String> = try {
        val a = JSONArray(decisionsJson); (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) { emptyList() }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("audioPath", audioPath)
        put("durationMs", durationMs); put("createdAt", createdAt); put("status", status)
        put("transcript", transcript); put("summary", summary)
        put("topicsJson", topicsJson); put("actionsJson", actionsJson); put("decisionsJson", decisionsJson)
    }

    companion object {
        const val STATUS_RECORDING = "RECORDING"
        const val STATUS_TRANSCRIBING = "TRANSCRIBING"
        const val STATUS_SUMMARIZING = "SUMMARIZING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"

        fun fromJson(o: JSONObject) = Meeting(
            id = o.getLong("id"),
            title = o.optString("title", "Toplanti"),
            audioPath = o.optString("audioPath", ""),
            durationMs = o.optLong("durationMs", 0L),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            status = o.optString("status", STATUS_DONE),
            transcript = o.optString("transcript", ""),
            summary = o.optString("summary", ""),
            topicsJson = o.optString("topicsJson", "[]"),
            actionsJson = o.optString("actionsJson", "[]"),
            decisionsJson = o.optString("decisionsJson", "[]")
        )
    }
}

data class ActionItem(
    val task: String,
    val assignee: String = "Belirtilmemis",
    val deadline: String = "",
    val priority: String = "ORTA"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("task", task); put("assignee", assignee)
        put("deadline", deadline); put("priority", priority)
    }
    companion object {
        fun fromJson(o: JSONObject) = ActionItem(
            task = o.optString("task", ""),
            assignee = o.optString("assignee", "Belirtilmemis"),
            deadline = o.optString("deadline", ""),
            priority = o.optString("priority", "ORTA")
        )
    }
}

class Store(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("meetily_store", Context.MODE_PRIVATE)

    fun groqKey(): String = prefs.getString("groq_key", "") ?: ""
    fun setGroqKey(v: String) = prefs.edit().putString("groq_key", v).apply()

    fun nvidiaModel(): String = prefs.getString("nvidia_model", DEFAULT_NVIDIA_MODEL) ?: DEFAULT_NVIDIA_MODEL
    fun setNvidiaModel(v: String) = prefs.edit().putString("nvidia_model", v).apply()

    fun list(): List<Meeting> {
        val raw = prefs.getString("meetings", "[]") ?: "[]"
        return try {
            val a = JSONArray(raw); (0 until a.length()).map { Meeting.fromJson(a.getJSONObject(it)) }
                .sortedByDescending { it.createdAt }
        } catch (e: Exception) { emptyList() }
    }

    private fun saveAll(items: List<Meeting>) {
        val a = JSONArray(); items.forEach { a.put(it.toJson()) }
        prefs.edit().putString("meetings", a.toString()).apply()
    }

    fun add(m: Meeting) { saveAll(list() + m) }

    fun update(id: Long, block: (Meeting) -> Unit) {
        val items = list().toMutableList()
        val i = items.indexOfFirst { it.id == id }
        if (i >= 0) { block(items[i]); saveAll(items) }
    }

    fun get(id: Long): Meeting? = list().firstOrNull { it.id == id }

    fun delete(id: Long) {
        val m = get(id)
        if (m != null && m.audioPath.isNotEmpty()) {
            try { java.io.File(m.audioPath).delete() } catch (e: Exception) {}
        }
        saveAll(list().filter { it.id != id })
    }

    fun nextId(): Long = (list().maxOfOrNull { it.id } ?: 0L) + 1L

    companion object {
        const val DEFAULT_NVIDIA_MODEL = "meta/llama-3.3-70b-instruct"
    }
}
