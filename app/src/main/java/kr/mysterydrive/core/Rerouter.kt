package kr.mysterydrive.core

import kotlin.math.roundToInt

sealed interface RerouteResult {
    data class Ok(val upcoming: List<Place>, val message: String) : RerouteResult
    data class Refused(val message: String) : RerouteResult
}

/**
 * Changes the stops still ahead. [reach] says how far a place is from where the car is right now
 * (the engine works that out from the current leg, so this class does not need coordinates).
 */
class Rerouter(private val roads: RoadData, private val places: List<Place> = PLACES) {

    fun apply(cmd: Cmd, visited: List<Place>, upcoming: List<Place>, soc: Double, fuel: FuelType, reach: (Place) -> Reach): RerouteResult {
        var up = upcoming
        val message: String
        when (cmd) {
            Cmd.Home -> { up = emptyList(); message = "그럼 집으로 안내할게요." }
            Cmd.Skip -> {
                if (up.isEmpty()) return RerouteResult.Refused("건너뛸 장소가 없어요. 이미 집으로 가는 길이에요.")
                up = up.drop(1); message = "다음 장소는 건너뛸게요."
            }
            Cmd.Charge -> {
                val station = places.filter { it.charger }.minByOrNull { reach(it).km } ?: return RerouteResult.Refused("근처에 ${fuel.station}가 없어요.")
                val km = reach(station).km
                if (km > soc * fuel.kmPerPct * 0.95) {
                    return RerouteResult.Refused("가장 가까운 ${fuel.station}까지 ${km.roundToInt()}km인데 남은 주행거리로는 닿지 않아요. 긴급 출동을 불러 주세요.")
                }
                return RerouteResult.Ok(listOf(station) + up, "가장 가까운 ${fuel.station}로 안내할게요.")
            }
            else -> {
                val old = up.firstOrNull()
                val rest = up.drop(1)
                val kind = (cmd as? Cmd.Kind)?.kind
                val best = places.filter { p ->
                    !p.charger && visited.none { it.id == p.id } && p.id != old?.id && rest.none { it.id == p.id } && (kind == null || p.kind == kind)
                }.minByOrNull { reach(it).km } ?: return RerouteResult.Refused("바꿀 만한 장소가 없어요.")
                up = listOf(best) + rest
                message = when (kind) {
                    null -> "다음 장소를 바꿨어요."
                    "휴식" -> "쉬어 갈 수 있는 곳으로 바꿨어요."
                    else -> "실내에서 즐길 수 있는 곳으로 바꿨어요."
                }
            }
        }
        val cap = soc * fuel.kmPerPct * 0.8
        if (cmd != Cmd.Home && needKm(up, reach) > cap) {
            return RerouteResult.Refused("${fuel.energy}가 부족해서 그 코스는 어려워요. 남은 안전 거리는 ${cap.roundToInt()}km예요.")
        }
        return RerouteResult.Ok(up, message)
    }

    private fun needKm(up: List<Place>, reach: (Place) -> Reach): Double {
        var km = reach(up.firstOrNull() ?: HOME).km
        for (i in 1 until up.size) km += roads.km(up[i - 1], up[i])
        if (up.isNotEmpty()) km += roads.km(up.last(), HOME)
        return km
    }
}
