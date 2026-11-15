package kr.mysterydrive.core

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Builds a mystery course from the user's conditions and the energy level. Pure logic, no Android. */
class Planner(private val roads: RoadData, private val places: List<Place> = PLACES) {

    /** Greedy builder: keeps picking the best-scoring place that still fits distance, time and budget. */
    fun plan(c: Conditions, soc: Double, fuel: FuelType = FuelType.EV, rnd: () -> Double = { Math.random() }): Course? {
        val range = soc * fuel.kmPerPct
        val usable = min(c.maxKm.toDouble(), range * 0.8)
        val budget = c.timeMin - cushion(c.timeMin)
        var pos = HOME
        var km = 0.0
        var minutes = 0
        var cost = 0
        val stops = mutableListOf<Place>()
        for (n in 0 until 5) {
            var best: Place? = null
            var bestScore = -1e9
            for (p in places) {
                if (p.charger || stops.any { it.id == p.id }) continue
                val d = roads.km(pos, p)
                val back = roads.km(p, HOME)
                if (km + d + back > usable) continue
                val room = budget - minutes - roads.min(pos, p) - roads.min(p, HOME) - STOP_OVERHEAD
                val stay = min(p.stay, floor(room).toInt()) // squeeze the stay to the time left
                if (stay < MIN_STAY) continue
                if (cost + p.cost > c.budget) continue
                val match = if (c.mood == "랜덤" || p.kind == c.mood) 2.0 else 0.0
                val variety = if (stops.isNotEmpty() && stops.last().kind == p.kind) -0.8 else 0.0
                val meal = if (c.timeMin >= 240 && stops.none { it.kind == "맛집" } && p.kind == "맛집") 1.0 else 0.0
                val score = match + variety + meal - d / 35 - (roads.min(pos, p) + stay) / (budget - minutes) * 1.5 +
                    (if (c.mood == "랜덤") rnd() * 1.2 else 0.0)
                if (score > bestScore) { bestScore = score; best = p.copy(rec = p.stay, stay = stay) }
            }
            val pick = best ?: break
            km += roads.km(pos, pick); minutes += roads.min(pos, pick) + STOP_OVERHEAD + pick.stay; cost += pick.cost
            stops += pick; pos = pick
        }
        if (stops.isEmpty()) return null

        var list: List<Place> = stops
        var course = build(list)
        // Long trip: try a charger before the last stop, keep it only if every limit still holds.
        if (course.totalKm > range * 0.5 && stops.size > 1) {
            val prev = stops[stops.size - 2]
            val charger = places.filter { it.charger }.minByOrNull { roads.km(prev, it) }
            if (charger != null) {
                val withCharger = stops.dropLast(1) + charger + stops.last()
                val c2 = build(withCharger)
                if (c2.totalKm <= usable && c2.totalMin <= budget) { course = c2; list = withCharger }
            }
        }
        return course.copy(stops = list, cost = cost, socAfter = soc - course.totalKm / fuel.kmPerPct)
    }

    private fun build(list: List<Place>): Course {
        val legs = mutableListOf<Leg>()
        var from = HOME
        for (s in list) { legs += Leg(s, roads.km(from, s), roads.min(from, s)); from = s }
        legs += Leg(null, roads.km(from, HOME), roads.min(from, HOME))
        return Course(list, legs, legs.sumOf { it.km }, legs.sumOf { it.min } + list.sumOf { it.stay + STOP_OVERHEAD }, 0, 0.0)
    }

    /** Explains why [plan] found nothing: looks at the closest place and names every limit it breaks. */
    fun whyNot(c: Conditions, soc: Double, fuel: FuelType = FuelType.EV): List<String> {
        val range = soc * fuel.kmPerPct
        val usable = min(c.maxKm.toDouble(), range * 0.8)
        val budget = c.timeMin - cushion(c.timeMin)
        class Probe(val p: Place, val roundTrip: Double, val minutes: Int, val miss: List<String>)
        var best: Probe? = null
        for (p in places) {
            if (p.charger) continue
            val rt = roads.km(HOME, p) + roads.km(p, HOME)
            val tm = roads.min(HOME, p) + roads.min(p, HOME) + MIN_STAY + STOP_OVERHEAD
            val miss = buildList {
                if (rt > usable) add("km")
                if (tm > budget) add("time")
                if (p.cost > c.budget) add("cost")
            }
            val cur = best
            if (cur == null || miss.size < cur.miss.size || (miss.size == cur.miss.size && rt < cur.roundTrip)) best = Probe(p, rt, tm, miss)
        }
        val b = best ?: return listOf("조건을 조금 넉넉하게 바꿔 보세요.")
        val tips = mutableListOf<String>()
        if ("time" in b.miss) tips += "시간: 가장 가까운 곳도 왕복 이동(교통 여유 포함)·체류·예비 시간까지 약 ${ceil(b.minutes / 0.9).toInt()}분이 걸려요. ${ceil(b.minutes / 0.9 / 30) * 30 / 60}시간 이상으로 늘려 보세요."
        if ("km" in b.miss) tips += if (c.maxKm < range * 0.8) "거리: 왕복 ${b.roundTrip.roundToInt()}km가 필요한데 최대 주행 거리가 ${c.maxKm}km로 설정돼 있어요."
        else "${fuel.energy}: 왕복 ${b.roundTrip.roundToInt()}km가 필요한데 ${soc.roundToInt()}%로는 안전하게 ${(range * 0.8).roundToInt()}km까지만 갈 수 있어요."
        if ("cost" in b.miss) tips += "예산: 가장 저렴한 곳도 ${String.format(Locale.KOREA, "%,d", b.p.cost)}원이 들어요."
        return tips.ifEmpty { listOf("조건을 조금 넉넉하게 바꿔 보세요.") }
    }
}
