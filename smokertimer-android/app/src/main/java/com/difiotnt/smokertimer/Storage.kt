package com.difiotnt.smokertimer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SmokingEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMillis: Long,
    val note: String = "",
)

data class SmokingSettings(
    val intervalMinutes: Int = 60,
    val notificationsEnabled: Boolean = true,
)

enum class HistoryRange(val label: String) {
    DAY("Hari"),
    WEEK("Minggu"),
    MONTH("Bulan"),
    ALL("Semua"),
}

data class DaySection(
    val dateKey: Long,
    val title: String,
    val entries: List<SmokingEntry>,
)

class SmokingRepository(context: Context) {
    private val entriesFile = File(context.filesDir, "smoking_entries.json")
    private val settingsFile = File(context.filesDir, "smoking_settings.json")

    fun loadEntries(): List<SmokingEntry> = readEntries().sortedByDescending { it.timestampMillis }

    fun loadSettings(): SmokingSettings {
        if (!settingsFile.exists()) return SmokingSettings()

        return runCatching {
            val json = JSONObject(settingsFile.readText())
            SmokingSettings(
                intervalMinutes = json.optInt("intervalMinutes", 60).coerceAtLeast(5),
                notificationsEnabled = json.optBoolean("notificationsEnabled", true),
            )
        }.getOrDefault(SmokingSettings())
    }

    fun saveEntries(entries: List<SmokingEntry>) {
        val payload = JSONArray()
        entries.forEach { payload.put(it.toJson()) }
        entriesFile.writeText(payload.toString())
    }

    fun saveSettings(settings: SmokingSettings) {
        val json = JSONObject().apply {
            put("intervalMinutes", settings.intervalMinutes)
            put("notificationsEnabled", settings.notificationsEnabled)
        }
        settingsFile.writeText(json.toString())
    }

    fun addSmoking(note: String): SmokingEntry {
        val entry = SmokingEntry(
            timestampMillis = System.currentTimeMillis(),
            note = note.trim(),
        )
        val updated = loadEntries() + entry
        saveEntries(updated.sortedByDescending { it.timestampMillis })
        return entry
    }

    private fun readEntries(): List<SmokingEntry> {
        if (!entriesFile.exists()) return emptyList()

        return runCatching {
            val json = JSONArray(entriesFile.readText())
            buildList {
                for (index in 0 until json.length()) {
                    add(json.getJSONObject(index).toSmokingEntry())
                }
            }
        }.getOrDefault(emptyList())
    }
}

private fun SmokingEntry.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("timestampMillis", timestampMillis)
    put("note", note)
}

private fun JSONObject.toSmokingEntry(): SmokingEntry = SmokingEntry(
    id = optString("id", UUID.randomUUID().toString()),
    timestampMillis = optLong("timestampMillis", System.currentTimeMillis()),
    note = optString("note", ""),
)
