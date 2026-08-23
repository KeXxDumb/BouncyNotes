package com.dumb.bouncynotes.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun fromNoteType(value: NoteType): String = value.name

    @TypeConverter
    fun toNoteType(value: String): NoteType =
        try { NoteType.valueOf(value) } catch (e: Exception) { NoteType.TEXT }

    @TypeConverter
    fun fromChecklist(items: List<ChecklistItem>): String {
        val arr = JSONArray()
        items.forEach {
            val o = JSONObject()
            o.put("t", it.text)
            o.put("c", it.checked)
            arr.put(o)
        }
        return arr.toString()
    }

    @TypeConverter
    fun toChecklist(json: String): List<ChecklistItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ChecklistItem(text = o.optString("t"), checked = o.optBoolean("c"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromLabels(labels: List<String>): String {
        val arr = JSONArray()
        labels.forEach { arr.put(it) }
        return arr.toString()
    }

    @TypeConverter
    fun toLabels(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Días de la semana del recordatorio recurrente, guardados como
    // "1,3,5" (valores de Calendar.DAY_OF_WEEK separados por coma).
    @TypeConverter
    fun fromReminderDays(days: Set<Int>): String = days.sorted().joinToString(",")

    @TypeConverter
    fun toReminderDays(value: String): Set<Int> {
        if (value.isBlank()) return emptySet()
        return value.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }
}
