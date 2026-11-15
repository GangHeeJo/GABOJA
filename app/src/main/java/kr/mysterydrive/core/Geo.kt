package kr.mysterydrive.core

import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh

/** A point on the map in kilometres: x grows eastward, y grows southward (screen-like). */
data class Pt(val x: Double, val y: Double)

/** Local flat projection around Seoul. Good enough for a 250 km wide area; the same one the web prototype uses. */
fun kmXY(lat: Double, lon: Double) = Pt((lon - 127.0) * 88.3, (37.6 - lat) * 111.3)
fun kmXY(p: Place) = kmXY(p.lat, p.lon)

/** Corner of a Web Mercator tile (z/x/y) in km, so tiles can be laid out on the same plane as the routes. */
fun tileCorner(x: Int, y: Int, z: Int): Pt {
    val n = 1 shl z
    val lon = x.toDouble() / n * 360.0 - 180.0
    val lat = atan(sinh(PI * (1 - 2.0 * y / n))) * 180.0 / PI
    return kmXY(lat, lon)
}

/** Decodes a Google-style encoded polyline (precision 5) into km points. */
fun decodePolyline(s: String): List<Pt> {
    val out = ArrayList<Pt>()
    var i = 0
    var lat = 0
    var lon = 0
    while (i < s.length) {
        for (k in 0..1) {
            var result = 0
            var shift = 0
            var b: Int
            do { b = s[i++].code - 63; result = result or ((b and 31) shl shift); shift += 5 } while (b >= 32)
            val d = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            if (k == 0) lat += d else lon += d
        }
        out += kmXY(lat / 1e5, lon / 1e5)
    }
    return out
}

/** Real road lines between every pair of known places (from OSRM), in km points. */
class RouteGeometry(private val ids: List<String>, private val geom: Map<String, String>) {
    private val cache = HashMap<String, List<Pt>>()

    /** The road from [a] to [b]; a straight line when the pair is unknown. Empty when both are the same place. */
    fun path(a: Place, b: Place): List<Pt> {
        if (a.id == b.id) return emptyList()
        val i = ids.indexOf(a.id)
        val j = ids.indexOf(b.id)
        val key = "${min(i, j)}-${max(i, j)}"
        if (i < 0 || j < 0 || key !in geom) return listOf(kmXY(a), kmXY(b))
        val pts = cache.getOrPut(key) { decodePolyline(geom.getValue(key)) }
        return if (i < j) pts else pts.reversed()
    }

    companion object {
        fun fromJson(text: String): RouteGeometry {
            val o = JSONObject(text)
            val ids = o.getJSONArray("ids").let { a -> List(a.length()) { a.getString(it) } }
            val g = o.getJSONObject("geom")
            return RouteGeometry(ids, g.keys().asSequence().associateWith { g.getString(it) })
        }
    }
}

/** Where a fraction [t] of the way along a polyline is, and which way the road points there. */
data class Along(val p: Pt, val k: Int, val headingDeg: Double)

fun along(pts: List<Pt>, t: Double): Along {
    require(pts.size >= 2) { "a road needs at least two points" }
    val cum = DoubleArray(pts.size)
    for (i in 1 until pts.size) cum[i] = cum[i - 1] + hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
    val total = if (cum.last() > 0) cum.last() else 1.0
    val d = t.coerceIn(0.0, 1.0) * total
    var k = 1
    while (k < pts.size - 1 && cum[k] < d) k++
    val seg = (cum[k] - cum[k - 1]).takeIf { it > 0 } ?: 1.0
    val u = ((d - cum[k - 1]) / seg).coerceIn(0.0, 1.0)
    val p = Pt(pts[k - 1].x + (pts[k].x - pts[k - 1].x) * u, pts[k - 1].y + (pts[k].y - pts[k - 1].y) * u)
    val heading = atan2(pts[k].x - pts[k - 1].x, -(pts[k].y - pts[k - 1].y)) * 180.0 / PI
    return Along(p, k, heading)
}

/** Length of a polyline in km. */
fun lengthKm(pts: List<Pt>): Double = pts.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y) }
