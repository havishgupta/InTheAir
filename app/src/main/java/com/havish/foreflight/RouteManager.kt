package com.havish.foreflight

import android.content.Context
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val speed: Double,
    val timestamp: Long
)

data class RouteData(
    var id: String,
    var name: String,
    val startTime: Long,
    var endTime: Long,
    val points: MutableList<RoutePoint> = mutableListOf()
)

class RouteManager(private val context: Context) {
    
    private val routesDir = File(context.filesDir, "routes").apply { mkdirs() }

    fun startNewRoute(): RouteData {
        val time = System.currentTimeMillis()
        val id = "route_$time"
        return RouteData(
            id = id,
            name = "Route ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(time))}",
            startTime = time,
            endTime = time
        )
    }

    fun saveRoute(route: RouteData) {
        val file = File(routesDir, "${route.id}.json")
        val root = JSONObject()
        root.put("id", route.id)
        root.put("name", route.name)
        root.put("startTime", route.startTime)
        root.put("endTime", route.endTime)
        
        val pointsArr = JSONArray()
        for (p in route.points) {
            val pObj = JSONObject()
            pObj.put("lat", p.lat)
            pObj.put("lon", p.lon)
            pObj.put("alt", p.alt)
            pObj.put("speed", p.speed)
            pObj.put("time", p.timestamp)
            pointsArr.put(pObj)
        }
        root.put("points", pointsArr)
        
        file.writeText(root.toString())
    }

    fun getSavedRoutes(): List<RouteData> {
        val files = routesDir.listFiles { _, name -> name.endsWith(".json") } ?: return emptyList()
        val list = mutableListOf<RouteData>()
        for (file in files) {
            try {
                val text = file.readText()
                val root = JSONObject(text)
                val rd = RouteData(
                    id = root.getString("id"),
                    name = root.getString("name"),
                    startTime = root.getLong("startTime"),
                    endTime = root.getLong("endTime")
                )
                val pArr = root.optJSONArray("points")
                if (pArr != null) {
                    for (i in 0 until pArr.length()) {
                        val pObj = pArr.getJSONObject(i)
                        rd.points.add(RoutePoint(
                            lat = pObj.getDouble("lat"),
                            lon = pObj.getDouble("lon"),
                            alt = pObj.getDouble("alt"),
                            speed = pObj.getDouble("speed"),
                            timestamp = pObj.getLong("time")
                        ))
                    }
                }
                list.add(rd)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list.sortedByDescending { it.startTime }
    }
    
    fun deleteRoute(id: String) {
        val file = File(routesDir, "$id.json")
        if (file.exists()) {
            file.delete()
        }
    }
    
    fun renameRoute(id: String, newName: String) {
        val routes = getSavedRoutes()
        val route = routes.find { it.id == id }
        if (route != null) {
            route.name = newName
            saveRoute(route)
        }
    }
}