package io.github.mrroguekknight.drishti.map

import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RouteFetcher {
    private const val OSRM_API_URL = "https://router.project-osrm.org/route/v1/driving/"

    suspend fun getRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> = withContext(Dispatchers.IO) {
        val waypoints = mutableListOf<GeoPoint>()
        try {
            val urlString = "$OSRM_API_URL${start.longitude},${start.latitude};${end.longitude},${end.latitude}?overview=full&geometries=geojson"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val routes = json.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                    val coordinates = geometry.getJSONArray("coordinates")
                    
                    for (i in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(i)
                        val lon = coord.getDouble(0)
                        val lat = coord.getDouble(1)
                        waypoints.add(GeoPoint(lat, lon))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RouteFetcher", "Error fetching route", e)
            // Fallback to straight line if fetch fails
            waypoints.add(start)
            waypoints.add(end)
        }
        return@withContext waypoints
    }
}
