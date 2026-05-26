package com.havish.foreflight

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class GlobalNote(
    val id: String,
    val lat: Double,
    val lon: Double,
    val text: String,
    val tag: String
)

class GlobalNotesManager(private val context: Context) {

    private val notesFile = File(context.filesDir, "global_notes.json")

    fun getNotes(): List<GlobalNote> {
        if (!notesFile.exists()) return emptyList()
        try {
            val content = notesFile.readText()
            val root = JSONObject(content)
            val notesArr = root.optJSONArray("notes") ?: return emptyList()
            val list = mutableListOf<GlobalNote>()
            for (i in 0 until notesArr.length()) {
                val obj = notesArr.getJSONObject(i)
                list.add(
                    GlobalNote(
                        id = obj.getString("id"),
                        lat = obj.getDouble("lat"),
                        lon = obj.getDouble("lon"),
                        text = obj.getString("text"),
                        tag = obj.getString("tag")
                    )
                )
            }
            return list
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun addNote(lat: Double, lon: Double, text: String, tag: String): GlobalNote {
        val notes = getNotes().toMutableList()
        val newNote = GlobalNote(
            id = UUID.randomUUID().toString(),
            lat = lat,
            lon = lon,
            text = text,
            tag = tag
        )
        notes.add(newNote)
        saveNotes(notes)
        return newNote
    }

    fun deleteNote(id: String) {
        val notes = getNotes().filter { it.id != id }
        saveNotes(notes)
    }

    private fun saveNotes(notes: List<GlobalNote>) {
        val root = JSONObject()
        val notesArr = JSONArray()
        for (n in notes) {
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("lat", n.lat)
            obj.put("lon", n.lon)
            obj.put("text", n.text)
            obj.put("tag", n.tag)
            notesArr.put(obj)
        }
        root.put("notes", notesArr)
        notesFile.writeText(root.toString())
    }

    fun getTags(): List<String> {
        return getNotes().map { it.tag }.distinct().sorted()
    }
}
