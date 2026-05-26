package com.havish.foreflight

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VoyagePoint(
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val speed: Double,
    val timestamp: Long
)

data class VoyageNote(
    val timestamp: Long,
    val text: String,
    val lat: Double,
    val lon: Double
)

data class VoyageData(
    var id: String,
    var name: String,
    val startTime: Long,
    var endTime: Long,
    var mode: String = "plane",
    val points: MutableList<VoyagePoint> = mutableListOf(),
    val notes: MutableList<VoyageNote> = mutableListOf()
)

class VoyageManager(private val context: Context) {
    
    // Keep directory as "routes" for backward compatibility with existing saved data
    private val voyagesDir = File(context.filesDir, "routes").apply { mkdirs() }

    fun startNewVoyage(mode: String = "plane"): VoyageData {
        val time = System.currentTimeMillis()
        val id = "route_$time"
        return VoyageData(
            id = id,
            name = "Voyage ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))}",
            startTime = time,
            endTime = time,
            mode = mode
        )
    }

    fun saveVoyage(voyage: VoyageData) {
        val file = File(voyagesDir, "${voyage.id}.json")
        val root = JSONObject()
        root.put("id", voyage.id)
        root.put("name", voyage.name)
        root.put("startTime", voyage.startTime)
        root.put("endTime", voyage.endTime)
        root.put("mode", voyage.mode)
        
        val pointsArr = JSONArray()
        for (p in voyage.points) {
            val pObj = JSONObject()
            pObj.put("lat", p.lat)
            pObj.put("lon", p.lon)
            pObj.put("alt", p.alt)
            pObj.put("speed", p.speed)
            pObj.put("time", p.timestamp)
            pointsArr.put(pObj)
        }
        root.put("points", pointsArr)
        
        val notesArr = JSONArray()
        for (n in voyage.notes) {
            val nObj = JSONObject()
            nObj.put("time", n.timestamp)
            nObj.put("text", n.text)
            nObj.put("lat", n.lat)
            nObj.put("lon", n.lon)
            notesArr.put(nObj)
        }
        root.put("notes", notesArr)
        
        file.writeText(root.toString())
    }

    fun getSavedVoyages(): List<VoyageData> {
        val files = voyagesDir.listFiles { _, name -> name.endsWith(".json") } ?: return emptyList()
        val list = mutableListOf<VoyageData>()
        for (file in files) {
            try {
                val text = file.readText()
                val root = JSONObject(text)
                val vd = VoyageData(
                    id = root.getString("id"),
                    name = root.getString("name"),
                    startTime = root.getLong("startTime"),
                    endTime = root.getLong("endTime"),
                    mode = root.optString("mode", "plane")
                )
                val pArr = root.optJSONArray("points")
                if (pArr != null) {
                    for (i in 0 until pArr.length()) {
                        val pObj = pArr.getJSONObject(i)
                        vd.points.add(VoyagePoint(
                            lat = pObj.getDouble("lat"),
                            lon = pObj.getDouble("lon"),
                            alt = pObj.getDouble("alt"),
                            speed = pObj.getDouble("speed"),
                            timestamp = pObj.getLong("time")
                        ))
                    }
                }
                val nArr = root.optJSONArray("notes")
                if (nArr != null) {
                    for (i in 0 until nArr.length()) {
                        val nObj = nArr.getJSONObject(i)
                        vd.notes.add(VoyageNote(
                            timestamp = nObj.getLong("time"),
                            text = nObj.getString("text"),
                            lat = nObj.getDouble("lat"),
                            lon = nObj.getDouble("lon")
                        ))
                    }
                }
                list.add(vd)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list.sortedByDescending { it.startTime }
    }
    
    fun deleteVoyage(id: String) {
        val file = File(voyagesDir, "$id.json")
        if (file.exists()) {
            file.delete()
        }
    }
    
    fun renameVoyage(id: String, newName: String) {
        val voyages = getSavedVoyages()
        val voyage = voyages.find { it.id == id }
        if (voyage != null) {
            voyage.name = newName
            saveVoyage(voyage)
        }
    }
}