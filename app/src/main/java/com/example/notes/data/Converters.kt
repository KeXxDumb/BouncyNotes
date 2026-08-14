package com.example.notes.data

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
    fun fromImages(images: List<NoteImage>): String {
        val arr = JSONArray()
        images.forEach {
            val o = JSONObject()
            o.put("p", it.path)
            o.put("cap", it.caption)
            arr.put(o)
        }
        return arr.toString()
    }

    @TypeConverter
    fun toImages(json: String): List<NoteImage> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                NoteImage(path = o.optString("p"), caption = o.optString("cap"))
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
}
