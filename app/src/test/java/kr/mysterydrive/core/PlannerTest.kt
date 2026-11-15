package kr.mysterydrive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlannerTest {
    private val roads = RoadData.fromJson(File("src/main/assets/roads.json").readText())
    private val planner = Planner(roads)
    private val rerouter = Rerouter(roads)
    private val fixed = { 0.5 }
    private val base = Conditions(timeMin = 240, budget = 100_000, maxKm = 150, mood = "랜덤", who = "연인")
    private val ev = FuelType.EV

    private fun place(id: String) = PLACES.first { it.id == id }
    private val fromHome: (Place) -> Reach = { Reach(roads.km(HOME, it), roads.min(HOME, it)) }

    @Test fun fourHoursLeavesRealSlack() { // conservative: a 4 hour trip must not be planned to the last minute
        val c = planner.plan(base, 100.0, rnd = fixed)
        assertNotNull(c)
        assertTrue("planned ${c!!.totalMin} min", c.totalMin <= 240 - cushion(240))
    }

    @Test fun twoHoursIsTooShortAndSaysSo() {
        assertNull(planner.plan(base.copy(timeMin = 120), 100.0, rnd = fixed))
        val why = planner.whyNot(base.copy(timeMin = 120), 100.0).single()
        assertTrue(why, why.startsWith("시간") && "교통 여유" in why)
    }

    @Test fun courseRespectsTimeDistanceAndBudget() {
        for (soc in listOf(100.0, 62.0, 30.0)) for (t in listOf(180, 240, 360)) {
            val c = planner.plan(base.copy(timeMin = t), soc, rnd = fixed) ?: continue
            assertTrue("time soc=$soc t=$t: ${c.totalMin}", c.totalMin <= t - cushion(t))
            assertTrue("km soc=$soc t=$t", c.totalKm <= minOf(150.0, soc * ev.kmPerPct * 0.8) + 0.01)
            assertTrue("budget", c.cost <= base.budget)
            assertTrue("every stay is at least $MIN_STAY min", c.stops.filter { !it.charger }.all { it.stay >= MIN_STAY })
            assertEquals(c.stops.size + 1, c.legs.size)
            assertNull(c.legs.last().to)
        }
    }

    @Test fun driveTimeIncludesTheTrafficMargin() {
        val a = place("dmg")
        val free = roads.min(HOME, a) / TRAFFIC
        assertTrue(free > 20) // sanity: this is a real road time, not a stub
        assertEquals(Math.round(free * TRAFFIC).toInt(), roads.min(HOME, a))
    }

    @Test fun lowBatteryRefusesAndExplains() {
        assertNull(planner.plan(base, 5.0, rnd = fixed))
        assertTrue(planner.whyNot(base, 5.0).single().startsWith("배터리"))
    }

    @Test fun gasolineCarsTalkAboutFuelAndGoFurtherPerPercent() {
        assertTrue(planner.whyNot(base, 5.0, FuelType.ICE).single().startsWith("연료"))
        assertEquals(600.0, 100 * FuelType.ICE.kmPerPct, 0.0)
        assertEquals("가평 휴게소 주유소", PLACES.first { it.charger }.label(FuelType.ICE))
        assertEquals("가평 휴게소 충전소", PLACES.first { it.charger }.label(ev))
    }

    @Test fun whyNamesTheBlockingLimit() {
        assertTrue(planner.whyNot(base.copy(timeMin = 30), 100.0).single().startsWith("시간"))
        assertTrue(planner.whyNot(base.copy(maxKm = 30), 100.0).single().startsWith("거리"))
        assertTrue(planner.whyNot(base.copy(budget = 1000), 100.0).single().startsWith("예산"))
    }

    @Test fun parsesTheSentenceFromTheBrief() {
        val p = parseVoice("오늘 4시간 안에, 10만 원 이하로, 맛있는 점심 포함해서 아무 데나 데려가 줘")
        assertEquals(ConditionsPatch(240, 100_000, "맛집", null), p)
        assertEquals(base.copy(timeMin = 240, mood = "맛집"), base.merge(p))
    }

    @Test fun parsesRouteCommands() {
        assertEquals(Cmd.Swap, parseCmd("다음 장소 바꿔줘"))
        assertEquals(Cmd.Skip, parseCmd("다음 장소는 건너뛰어줘"))
        assertEquals(Cmd.Kind("휴식"), parseCmd("조금 쉬고 싶어"))
        assertEquals(Cmd.Kind("문화"), parseCmd("비가 와, 실내로 가자"))
        assertEquals(Cmd.Home, parseCmd("그만 집에 갈래"))
        assertEquals(Cmd.Charge, parseCmd("근처 충전소 안내해줘"))
        assertEquals(Cmd.Charge, parseCmd("근처 주유소 안내해줘"))
        assertNull(parseCmd("안녕"))
    }

    @Test fun swapPicksADifferentNearestPlace() {
        val r = rerouter.apply(Cmd.Swap, emptyList(), listOf(place("dmg")), 100.0, ev, fromHome) as RerouteResult.Ok
        assertNotEquals("dmg", r.upcoming.first().id)
        assertEquals(1, r.upcoming.size)
    }

    @Test fun skipDropsTheNextStopAndHomeEmptiesTheList() {
        val up = listOf(place("dmg"), place("pet"))
        assertEquals(listOf("pet"), (rerouter.apply(Cmd.Skip, emptyList(), up, 100.0, ev, fromHome) as RerouteResult.Ok).upcoming.map { it.id })
        assertTrue((rerouter.apply(Cmd.Home, emptyList(), up, 100.0, ev, fromHome) as RerouteResult.Ok).upcoming.isEmpty())
        assertTrue(rerouter.apply(Cmd.Skip, emptyList(), emptyList(), 100.0, ev, fromHome) is RerouteResult.Refused)
    }

    @Test fun kindCommandsPickThatKind() {
        val r = rerouter.apply(Cmd.Kind("휴식"), listOf(place("dmg")), listOf(place("pet")), 100.0, ev, fromHome) as RerouteResult.Ok
        assertEquals("휴식", r.upcoming.first().kind)
    }

    @Test fun chargeCommandInsertsTheNearestStationFirst() {
        val up = listOf(place("dmg"))
        val r = rerouter.apply(Cmd.Charge, emptyList(), up, 30.0, ev, fromHome) as RerouteResult.Ok
        assertTrue(r.upcoming.first().charger)
        assertEquals("dmg", r.upcoming[1].id)
        assertTrue(rerouter.apply(Cmd.Charge, emptyList(), up, 1.0, ev, fromHome) is RerouteResult.Refused) // too empty to reach one
    }

    @Test fun lowBatteryRefusesARouteChangeButNotGoingHome() {
        val up = listOf(place("dmg"))
        assertTrue(rerouter.apply(Cmd.Swap, emptyList(), up, 4.0, ev, fromHome) is RerouteResult.Refused)
        assertTrue(rerouter.apply(Cmd.Home, emptyList(), up, 4.0, ev, fromHome) is RerouteResult.Ok)
    }

    @Test fun everyPlaceHasRoadData() {
        for (p in PLACES + HOME) assertTrue("${p.id} missing", p.id in roads.ids)
    }
}
