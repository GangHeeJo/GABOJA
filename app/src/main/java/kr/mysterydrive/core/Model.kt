package kr.mysterydrive.core

import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

/** Energy model of the car. The Pleos emulator reports 500 km at 100% battery. */
enum class FuelType(val kmPerPct: Double, val station: String, val energy: String, val targetPct: Int, val ratePctPerMin: Double) {
    EV(5.0, "충전소", "배터리", 80, 4.0),   // fast charging to 80%, about 4% per minute
    ICE(6.0, "주유소", "연료", 100, 20.0),  // refuelling to 100%, about 20% per minute
}

// Conservative time planning: free-flow road time is stretched, every stop costs parking/walking time,
// no stay is shorter than MIN_STAY, and a cushion of the total time is never planned.
const val TRAFFIC = 1.15
const val STOP_OVERHEAD = 10
const val MIN_STAY = 30
const val LOW_PCT = 20
const val CRIT_PCT = 10
fun cushion(totalMin: Int): Double = max(15.0, totalMin * 0.1)

/** A place we can send the user to. [stay] is what the plan allows, [rec] is the recommended visit length. */
data class Place(
    val id: String,
    val name: String,
    val kind: String,
    val lat: Double,
    val lon: Double,
    val stay: Int,
    val cost: Int,
    val hint: String,
    val act: String,
    val charger: Boolean = false,
    val about: String? = null,
    val rec: Int = stay,
) {
    /** Charger stops are named after what the car needs: charger for an EV, gas station otherwise. */
    fun label(fuel: FuelType): String = if (charger) name.replace(Regex("충전소|주유소"), fuel.station) else name
}

val HOME = Place("home", "서울", "", 37.5665, 126.978, 0, 0, "", "")

data class Conditions(val timeMin: Int = 240, val budget: Int = 100_000, val maxKm: Int = 150, val mood: String = "랜덤", val who: String = "연인")

/** One drive: [to] is null for the final leg back home. A [virtual] leg starts from the car's current spot, not from a stop. */
data class Leg(val to: Place?, val km: Double, val min: Int, val virtual: Boolean = false)

data class Course(val stops: List<Place>, val legs: List<Leg>, val totalKm: Double, val totalMin: Int, val cost: Int, val socAfter: Double)

data class Reach(val km: Double, val min: Int)

/** Road distance (km) and drive time (min) between every pair of known places, precomputed from OSRM. */
class RoadData(val ids: List<String>, private val dist: Array<DoubleArray>, private val dur: Array<DoubleArray>) {
    private val index = ids.withIndex().associate { it.value to it.index }
    fun km(a: Place, b: Place): Double = dist[index.getValue(a.id)][index.getValue(b.id)]
    /** Drive time with the traffic margin already applied. */
    fun min(a: Place, b: Place): Int = (dur[index.getValue(a.id)][index.getValue(b.id)] * TRAFFIC).roundToInt()

    companion object {
        fun fromJson(text: String): RoadData {
            val o = JSONObject(text)
            val ids = o.getJSONArray("ids").let { a -> List(a.length()) { a.getString(it) } }
            fun matrix(key: String) = o.getJSONArray(key).let { rows -> Array(rows.length()) { r -> rows.getJSONArray(r).let { c -> DoubleArray(c.length()) { c.getDouble(it) } } } }
            return RoadData(ids, matrix("dist"), matrix("dur"))
        }
    }
}
