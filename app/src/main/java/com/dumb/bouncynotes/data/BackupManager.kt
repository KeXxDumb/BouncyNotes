package com.dumb.bouncynotes.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    fun exportNotes(context: Context, destUri: Uri, notes: List<Note>): Boolean {
        return try {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    val notesArray = JSONArray()
                    notes.forEach { notesArray.put(noteToJson(it)) }
                    zip.putNextEntry(ZipEntry("notes.json"))
                    zip.write(notesArray.toString().toByteArray())
                    zip.closeEntry()

                    val allFileNames = notes.flatMap { extractImageFileNames(it.content) }.distinct()
                    val imagesDir = ImageStorage.imagesDir(context)
                    allFileNames.forEach { fileName ->
                        val file = File(imagesDir, fileName)
                        if (file.exists()) {
                            zip.putNextEntry(ZipEntry("images/$fileName"))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun importNotes(context: Context, srcUri: Uri): List<Note> {
        val result = mutableListOf<Note>()
        try {
            context.contentResolver.openInputStream(srcUri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var notesJson: String? = null
                    val imagesDir = ImageStorage.imagesDir(context)
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (name == "notes.json") {
                            notesJson = zip.readBytes().decodeToString()
                        } else if (name.startsWith("images/")) {
                            val fileName = name.removePrefix("images/")
                            File(imagesDir, fileName).outputStream().use { out -> zip.copyTo(out) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    if (notesJson != null) {
                        val arr = JSONArray(notesJson)
                        for (i in 0 until arr.length()) {
                            result.add(jsonToNote(arr.getJSONObject(i)))
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
        return result
    }

    private fun noteToJson(note: Note): JSONObject {
        val o = JSONObject()
        o.put("type", note.type.name)
        o.put("title", note.title)
        o.put("content", note.content)
        o.put("color", note.color ?: JSONObject.NULL)
        o.put("pinned", note.pinned)
        o.put("archived", note.archived)
        o.put("isPrivate", note.isPrivate)
        o.put("deletedAt", note.deletedAt ?: JSONObject.NULL)
        o.put("createdAt", note.createdAt)
        o.put("updatedAt", note.updatedAt)

        val labelsArr = JSONArray()
        note.labels.forEach { labelsArr.put(it) }
        o.put("labels", labelsArr)

        val itemsArr = JSONArray()
        note.checklistItems.forEach {
            val io = JSONObject()
            io.put("text", it.text)
            io.put("checked", it.checked)
            itemsArr.put(io)
        }
        o.put("checklistItems", itemsArr)
        return o
    }

    private fun jsonToNote(o: JSONObject): Note {
        val type = try { NoteType.valueOf(o.optString("type")) } catch (e: Exception) { NoteType.TEXT }
        val labels = mutableListOf<String>()
        o.optJSONArray("labels")?.let { arr -> for (i in 0 until arr.length()) labels.add(arr.getString(i)) }

        val items = mutableListOf<ChecklistItem>()
        o.optJSONArray("checklistItems")?.let { arr ->
            for (i in 0 until arr.length()) {
                val io = arr.getJSONObject(i)
                items.add(ChecklistItem(text = io.optString("text"), checked = io.optBoolean("checked")))
            }
        }

        return Note(
            id = 0,
            type = type,
            title = o.optString("title"),
            content = o.optString("content"),
            checklistItems = items,
            labels = labels,
            color = if (o.isNull("color")) null else o.optString("color"),
            pinned = o.optBoolean("pinned"),
            archived = o.optBoolean("archived"),
            isPrivate = o.optBoolean("isPrivate"),
            deletedAt = if (o.isNull("deletedAt")) null else o.optLong("deletedAt"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
