package com.havish.foreflight

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * Custom tile downloader that bypasses osmdroid's CacheManager
 * (which crashes on many devices) and instead downloads tiles
 * one by one using OkHttp, writing them directly into osmdroid's
 * tile cache via the map's own tile writer.
 */
class TileDownloadHelper(private val mapView: MapView) {

    companion object {
        private const val TAG = "TileDownload"
        private const val TILE_URL = "https://tile.openstreetmap.org/%d/%d/%d.png"
        private const val RATE_LIMIT_MS = 300L // 3-4 tiles/sec, within OSM tile policy
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    interface Callback {
        fun onStart(totalTiles: Int)
        fun onProgress(completed: Int, total: Int, currentZoom: Int)
        fun onComplete(downloaded: Int, errors: Int)
        fun onError(message: String)
    }

    /**
     * Downloads tiles for the given bounding box and zoom range.
     * Returns a Job that can be cancelled to stop the download.
     */
    fun downloadArea(
        boundingBox: BoundingBox,
        zoomMin: Int,
        zoomMax: Int,
        callback: Callback
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                val tiles = calculateTiles(boundingBox, zoomMin, zoomMax)
                Log.d(TAG, "Total tiles to download: ${tiles.size}")

                withContext(Dispatchers.Main) { callback.onStart(tiles.size) }

                if (tiles.isEmpty()) {
                    withContext(Dispatchers.Main) { callback.onComplete(0, 0) }
                    return@launch
                }

                val tileWriter = mapView.tileProvider.tileWriter
                if (tileWriter == null) {
                    Log.e(TAG, "tileWriter is null!")
                    withContext(Dispatchers.Main) { callback.onError("Tile cache not available") }
                    return@launch
                }

                val tileSource = TileSourceFactory.MAPNIK
                var completed = 0
                var errors = 0
                var lastZoom = tiles.first().first

                for ((z, x, y) in tiles) {
                    if (!isActive) break // Cancelled

                    try {
                        val url = String.format(TILE_URL, z, x, y)
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", "InTheAir/1.0 (foreflight-clone)")
                            .build()

                        val response = client.newCall(request).execute()
                        response.use { resp ->
                            if (resp.isSuccessful) {
                                val bytes = resp.body?.bytes()
                                if (bytes != null && bytes.isNotEmpty()) {
                                    val index = MapTileIndex.getTileIndex(z, x, y)
                                    tileWriter.saveFile(
                                        tileSource,
                                        index,
                                        ByteArrayInputStream(bytes),
                                        null
                                    )
                                } else {
                                    errors++
                                }
                            } else {
                                errors++
                                Log.w(TAG, "HTTP ${resp.code} for z=$z x=$x y=$y")
                                if (resp.code == 429) {
                                    // Rate limited — back off
                                    delay(3000)
                                }
                                Unit
                            }
                        }
                    } catch (e: Exception) {
                        errors++
                        Log.w(TAG, "Tile error z=$z x=$x y=$y: ${e.message}")
                    }

                    completed++
                    lastZoom = z

                    // Update progress every 5 tiles or on zoom level change or at the end
                    if (completed % 5 == 0 || completed == tiles.size) {
                        withContext(Dispatchers.Main) {
                            callback.onProgress(completed, tiles.size, lastZoom)
                        }
                    }

                    delay(RATE_LIMIT_MS)
                }

                withContext(Dispatchers.Main) {
                    callback.onComplete(completed, errors)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback.onError("Download failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Calculates all tile coordinates needed for the given bounding box and zoom range.
     */
    fun calculateTiles(
        bb: BoundingBox,
        zoomMin: Int,
        zoomMax: Int
    ): List<Triple<Int, Int, Int>> {
        val tiles = mutableListOf<Triple<Int, Int, Int>>()
        for (z in zoomMin..zoomMax) {
            val n = 1 shl z
            val xMin = lonToTileX(bb.lonWest, z).coerceIn(0, n - 1)
            val xMax = lonToTileX(bb.lonEast, z).coerceIn(0, n - 1)
            val yMin = latToTileY(bb.latNorth, z).coerceIn(0, n - 1)
            val yMax = latToTileY(bb.latSouth, z).coerceIn(0, n - 1)
            for (x in xMin..xMax) {
                for (y in yMin..yMax) {
                    tiles.add(Triple(z, x, y))
                }
            }
            Log.d(TAG, "Zoom $z: ${(xMax - xMin + 1) * (yMax - yMin + 1)} tiles")
        }
        return tiles
    }

    /** Converts longitude to tile X coordinate (slippy map tilenames) */
    private fun lonToTileX(lon: Double, zoom: Int): Int {
        return ((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    /** Converts latitude to tile Y coordinate (slippy map tilenames) */
    private fun latToTileY(lat: Double, zoom: Int): Int {
        val latClamped = lat.coerceIn(-85.0511, 85.0511)
        val latRad = Math.toRadians(latClamped)
        return ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * (1 shl zoom)).toInt()
    }
}
