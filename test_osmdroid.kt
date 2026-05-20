import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.views.MapView

fun test(map: MapView) {
    val cm = CacheManager(map)
    cm.downloadAreaAsyncNoUI(null, null, 1, 1, null)
}
