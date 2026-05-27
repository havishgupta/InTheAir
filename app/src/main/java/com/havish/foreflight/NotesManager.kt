package com.havish.foreflight

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Note(
    val id: String,
    val lat: Double,
    val lon: Double,
    val text: String,
    val tag: String,
    val icon: String
)

class NotesManager(private val context: Context) {

    private val notesFile = File(context.filesDir, "global_notes.json")

    fun getNotes(): List<Note> {
        if (!notesFile.exists()) return emptyList()
        try {
            val content = notesFile.readText()
            val root = JSONObject(content)
            val notesArr = root.optJSONArray("notes") ?: return emptyList()
            val list = mutableListOf<Note>()
            for (i in 0 until notesArr.length()) {
                val obj = notesArr.getJSONObject(i)
                list.add(
                    Note(
                        id = obj.getString("id"),
                        lat = obj.getDouble("lat"),
                        lon = obj.getDouble("lon"),
                        text = obj.getString("text"),
                        tag = obj.getString("tag"),
                        icon = obj.optString("icon", "ic_note_marker")
                    )
                )
            }
            return list
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun addNote(lat: Double, lon: Double, text: String, tag: String, icon: String = "ic_note_marker"): Note {
        val notes = getNotes().toMutableList()
        val newNote = Note(
            id = UUID.randomUUID().toString(),
            lat = lat,
            lon = lon,
            text = text,
            tag = tag,
            icon = icon
        )
        notes.add(newNote)
        saveNotes(notes)
        return newNote
    }

    fun deleteNote(id: String) {
        val notes = getNotes().filter { it.id != id }
        saveNotes(notes)
    }

    private fun saveNotes(notes: List<Note>) {
        val root = JSONObject()
        val notesArr = JSONArray()
        for (n in notes) {
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("lat", n.lat)
            obj.put("lon", n.lon)
            obj.put("text", n.text)
            obj.put("tag", n.tag)
            obj.put("icon", n.icon)
            notesArr.put(obj)
        }
        root.put("notes", notesArr)
        notesFile.writeText(root.toString())
    }

    fun getTags(): List<String> {
        return getNotes().map { it.tag }.distinct().sorted()
    }
}
