package kr.mysterydrive.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.mysterydrive.vehicle.Gear
import kotlin.math.roundToInt

enum class Screen { INPUT, FAIL, DRIVING, ARRIVED, DONE }
enum class Rating { UP, DOWN }
enum class AlertLevel { LOW, CRIT }

/** Charging or refuelling at a station stop. */
data class ChargeState(val startPct: Double, val target: Int, val active: Boolean = false, val done: Boolean = false)

/** Everything the screens need. The engine is the only thing that changes it. */
data class TripState(
    val screen: Screen = Screen.INPUT,
    val cond: Conditions = Conditions(),
    val fuel: FuelType = FuelType.EV,
    val soc: Double = 100.0,
    val gear: Gear = Gear.PARK,
    val course: Course? = null,
    val leg: Int = 0,
    val progKm: Double = 0.0,
    val extraKm: Double = 0.0,
    /** Roads already driven on a leg that was changed half way (the map keeps drawing them). */
    val trail: List<List<Pt>> = emptyList(),
    val rated: Map<String, Rating> = emptyMap(),
    val visits: Map<String, Set<Int>> = emptyMap(),
    val fail: List<String> = emptyList(),
    val tts: String = "",
    val alert: AlertLevel? = null,
    val offer: Int = 0,
    val charge: ChargeState? = null,
    val log: List<String> = emptyList(),
) {
    val currentLeg: Leg? get() = course?.legs?.getOrNull(leg)
    val currentStop: Place? get() = currentLeg?.to
    val nextLeg: Leg? get() = course?.legs?.getOrNull(leg + 1)
    val rangeKm: Double get() = soc * fuel.kmPerPct
}

/**
 * The trip, step by step: plan, drive, arrive, change route, low fuel, charge. Pure Kotlin so it can be tested
 * without a car. The screens only show [state] and call these functions.
 * [geometry] gives every leg its real road line for the map; without it legs are drawn as straight lines.
 */
class TripEngine(
    private val planner: Planner,
    private val rerouter: Rerouter,
    private val roads: RoadData,
    private val geometry: RouteGeometry? = null,
    private val rnd: () -> Double = { Math.random() },
) {
    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state

    private fun update(f: TripState.() -> TripState) { _state.value = _state.value.f() }
    private fun TripState.say(text: String) = copy(tts = text, log = (log + text).takeLast(60))

    /** The road between two places, always at least two points. */
    private fun segment(a: Place, b: Place): List<Pt> = geometry?.path(a, b)?.takeIf { it.size >= 2 } ?: listOf(kmXY(a), kmXY(b))

    /** The road of leg [k]: its own path if it has one, otherwise from the previous stop to its destination. */
    fun legPoints(c: Course, k: Int): List<Pt> {
        val leg = c.legs[k]
        leg.path?.let { if (it.size >= 2) return it }
        val from = if (k == 0) HOME else c.legs[k - 1].to ?: HOME
        return segment(from, leg.to ?: HOME)
    }

    fun setConditions(c: Conditions) = update { copy(cond = c) }

    /** Values from the car. Null means the car did not report it, so the old value stays. */
    fun onVehicle(energyPct: Double?, gear: Gear, type: FuelType?) = update { copy(soc = energyPct ?: soc, gear = gear, fuel = type ?: fuel) }

    fun start() {
        val s = _state.value
        val course = planner.plan(s.cond, s.soc, s.fuel, rnd)
        if (course == null) {
            update { copy(screen = Screen.FAIL, course = null, fail = planner.whyNot(cond, soc, fuel)) }
            return
        }
        update {
            copy(screen = Screen.DRIVING, course = course, leg = 0, progKm = 0.0, extraKm = 0.0, trail = emptyList(), rated = emptyMap(), visits = emptyMap(),
                offer = 0, charge = null, alert = null, fail = emptyList())
                .say("오늘의 첫 번째 장소는 ${course.legs[0].min}분 뒤 공개됩니다. 안전하게 출발하세요.")
        }
    }

    fun arrive() = update { arrived() }

    private fun TripState.arrived(): TripState {
        val to = currentLeg?.to ?: return copy(screen = Screen.DONE, charge = null, alert = null).say("여행이 끝났어요. 오늘의 기록을 정리했어요.")
        return copy(screen = Screen.ARRIVED, alert = null, charge = if (to.charger) ChargeState(soc, fuel.targetPct) else null)
            .say("목적지 근처예요. 안전하게 정차해 주세요.")
    }

    fun next() = update {
        val c = course ?: return@update this
        val l = c.legs.getOrNull(leg + 1) ?: return@update this
        copy(leg = leg + 1, progKm = 0.0, screen = Screen.DRIVING, charge = null, alert = null)
            .say(if (l.to != null) "다음 장소는 ${l.min}분 뒤 공개됩니다." else "이제 집으로 돌아가요. ${l.min}분 걸려요.")
    }

    /**
     * Moves time forward. On the emulator nothing really drives, so while the gear is D the trip advances by
     * [speedKmh] x [timeScale]; the real app would take position from the car instead.
     */
    fun tick(dtSec: Double, speedKmh: Double = 60.0, timeScale: Double = 1.0) = update {
        val ch = charge
        if (screen == Screen.ARRIVED && ch != null && ch.active && gear == Gear.PARK) {
            val energy = minOf(ch.target.toDouble(), soc + dtSec * timeScale / 60.0 * fuel.ratePctPerMin)
            return@update if (energy >= ch.target) {
                copy(soc = energy, charge = ch.copy(active = false, done = true), offer = 0)
                    .say(if (fuel == FuelType.EV) "충전이 끝났어요. 출발할 수 있어요." else "주유가 끝났어요. 출발할 수 있어요.")
            } else copy(soc = energy)
        }
        val l = currentLeg
        if (screen != Screen.DRIVING || gear != Gear.DRIVE || l == null) return@update this
        val dkm = speedKmh / 3600.0 * dtSec * timeScale
        val moved = copy(progKm = minOf(l.km, progKm + dkm), soc = maxOf(0.0, soc - dkm / fuel.kmPerPct))
        when {
            moved.progKm >= l.km -> moved.arrived()
            moved.alert == null -> moved.lowFuelCheck()
            else -> moved
        }
    }

    /** At 20% we ask, at 10% we insist. */
    private fun TripState.lowFuelCheck(): TripState {
        val pct = soc.roundToInt()
        return when {
            soc <= CRIT_PCT && offer < 2 -> copy(offer = 2, alert = AlertLevel.CRIT).say("${fuel.energy}가 ${pct}%예요. 지금 가까운 ${fuel.station}로 가야 해요.")
            soc <= LOW_PCT && offer < 1 -> copy(offer = 1, alert = AlertLevel.LOW).say("${fuel.energy}가 ${pct}% 남았어요. 가까운 ${fuel.station}로 안내할까요?")
            else -> this
        }
    }

    fun alertDismiss() = update { copy(alert = null) }
    fun alertGo() { update { copy(alert = null) }; reroute(Cmd.Charge) }

    fun startCharge() = update {
        val ch = charge ?: return@update this
        if (gear != Gear.PARK) this else copy(charge = ch.copy(active = true)).say(if (fuel == FuelType.EV) "충전을 시작해요." else "주유를 시작해요.")
    }

    fun rate(placeId: String, r: Rating) = update { copy(rated = rated + (placeId to r)) }

    fun toggleVisit(placeId: String, index: Int) = update {
        val cur = visits[placeId].orEmpty()
        copy(visits = visits + (placeId to if (index in cur) cur - index else cur + index))
    }

    fun reset() = update { copy(screen = Screen.INPUT, course = null, charge = null, alert = null, leg = 0, progKm = 0.0, extraKm = 0.0, trail = emptyList()) }

    /**
     * Change the stops ahead, from a stop (exact distances) or from the middle of a leg (go on to the old target,
     * or turn back to the previous stop). The new first leg carries its own road line starting at the car.
     */
    fun reroute(cmd: Cmd) {
        val s = _state.value
        val c = s.course ?: return
        val l = s.currentLeg ?: return
        val atStop = s.screen == Screen.ARRIVED
        if (s.screen != Screen.DRIVING && !atStop) return
        val dest = l.to ?: HOME
        val prev = if (s.leg == 0) HOME else c.legs[s.leg - 1].to ?: HOME
        val t = if (l.km > 0) (s.progKm / l.km).coerceIn(0.0, 1.0) else 1.0
        val aheadMin = (l.min * (1 - t)).roundToInt()
        val cur = legPoints(c, s.leg)
        val here = along(cur, t)
        val ahead = listOf(here.p) + cur.drop(here.k)        // from the car to the end of this leg
        val behind = cur.take(here.k) + here.p               // what has been driven on this leg
        fun seg(a: Place, b: Place) = if (a.id == b.id) emptyList() else segment(a, b)

        /** How far and how long a place is from here, and the road line to it. */
        val choose: (Place) -> Pair<Reach, List<Pt>> = if (atStop) { x -> Reach(roads.km(dest, x), roads.min(dest, x)) to seg(dest, x) } else { x ->
            val viaForward = (l.km - s.progKm) + roads.km(dest, x)
            val viaBack = if (l.virtual) Double.POSITIVE_INFINITY else s.progKm + roads.km(prev, x) // a changed leg can only go on; change again at the next stop
            if (viaForward <= viaBack) Reach(viaForward, aheadMin + roads.min(dest, x)) to (ahead + seg(dest, x))
            else Reach(viaBack, (l.min - aheadMin) + roads.min(prev, x)) to (behind.reversed() + seg(prev, x))
        }
        val reach: (Place) -> Reach = { choose(it).first }

        val keep = if (atStop) s.leg + 1 else s.leg
        val visited = c.legs.take(keep).mapNotNull { it.to }
        val upcoming = c.legs.drop(keep).mapNotNull { it.to }
        when (val r = rerouter.apply(cmd, visited, upcoming, s.soc, s.fuel, reach)) {
            is RerouteResult.Refused -> update { say(r.message) }
            is RerouteResult.Ok -> {
                val up = r.upcoming
                val (r0, path0) = choose(up.firstOrNull() ?: HOME)
                val legs2 = mutableListOf(Leg(up.firstOrNull(), r0.km, r0.min, virtual = !atStop, path = path0.takeIf { it.size >= 2 }))
                for (i in 1 until up.size) legs2 += Leg(up[i], roads.km(up[i - 1], up[i]), roads.min(up[i - 1], up[i]))
                if (up.isNotEmpty()) legs2 += Leg(null, roads.km(up.last(), HOME), roads.min(up.last(), HOME))
                val legs = c.legs.take(keep) + legs2
                val tail = if (up.isNotEmpty()) "새 목적지는 ${legs2[0].min}분 뒤 공개돼요." else "집까지 ${legs2[0].min}분 걸려요."
                update {
                    copy(course = c.copy(stops = visited + up, legs = legs, totalKm = legs.sumOf { it.km } + extraKm + (if (atStop) 0.0 else s.progKm)),
                        progKm = if (atStop) progKm else 0.0, extraKm = if (atStop) extraKm else extraKm + s.progKm,
                        trail = if (atStop) trail else trail + listOf(behind))
                        .say("${r.message} $tail")
                }
            }
        }
    }
}
