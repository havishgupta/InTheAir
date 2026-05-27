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
    val tag: String,           // Primary tag (kept for backward compatibility)
    val tags: List<String>,    // All tags this note belongs to
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
                val primaryTag = obj.getString("tag")

                // Migration: read tags array if present, otherwise wrap single tag
                val tagsArr = obj.optJSONArray("tags")
                val tagsList = if (tagsArr != null) {
                    (0 until tagsArr.length()).map { tagsArr.getString(it) }
                } else {
                    listOf(primaryTag)
                }

                list.add(
                    Note(
                        id = obj.getString("id"),
                        lat = obj.getDouble("lat"),
                        lon = obj.getDouble("lon"),
                        text = obj.getString("text"),
                        tag = primaryTag,
                        tags = tagsList,
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
        return addNote(lat, lon, text, listOf(tag), icon)
    }

    fun addNote(lat: Double, lon: Double, text: String, tags: List<String>, icon: String = "ic_note_marker"): Note {
        val notes = getNotes().toMutableList()
        val primaryTag = tags.firstOrNull() ?: "General"
        val newNote = Note(
            id = UUID.randomUUID().toString(),
            lat = lat,
            lon = lon,
            text = text,
            tag = primaryTag,
            tags = tags,
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

    fun updateNote(id: String, newText: String? = null, newTags: List<String>? = null, newIcon: String? = null) {
        val notes = getNotes().map { note ->
            if (note.id == id) {
                val updatedTags = newTags ?: note.tags
                Note(
                    id = note.id,
                    lat = note.lat,
                    lon = note.lon,
                    text = newText ?: note.text,
                    tag = updatedTags.firstOrNull() ?: note.tag,
                    tags = updatedTags,
                    icon = newIcon ?: note.icon
                )
            } else note
        }
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
            // Save all tags
            val tagsArr = JSONArray()
            for (t in n.tags) tagsArr.put(t)
            obj.put("tags", tagsArr)
            notesArr.put(obj)
        }
        root.put("notes", notesArr)
        notesFile.writeText(root.toString())
    }

    fun getTags(): List<String> {
        return getNotes().flatMap { it.tags }.distinct().sorted()
    }

    fun getNotesForTag(tag: String): List<Note> {
        return getNotes().filter { tag in it.tags }
    }
}
