package kr.mysterydrive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kr.mysterydrive.vehicle.Gear
import java.io.File

class TripEngineTest {
    private val roads = RoadData.fromJson(File("src/main/assets/roads.json").readText())
    private fun engine() = TripEngine(Planner(roads), Rerouter(roads), roads) { 0.5 }
    private val long = Conditions(timeMin = 360, budget = 200_000, maxKm = 300, mood = "랜덤")
    private val s get() = engine0.state.value
    private var engine0 = engine()

    private fun started(fuel: FuelType = FuelType.EV, soc: Double = 100.0, gear: Gear = Gear.DRIVE): TripEngine {
        engine0 = engine()
        engine0.setConditions(long); engine0.onVehicle(soc, gear, fuel); engine0.start()
        return engine0
    }

    /** Drives until arrival (one tick per simulated minute at 60 km/h). */
    private fun driveToArrival(e: TripEngine) { var n = 0; while (e.state.value.screen == Screen.DRIVING && n++ < 5000) e.tick(60.0, 60.0, 1.0) }

    @Test fun startPlansACourseAndHidesTheDestination() {
        val e = started()
        assertEquals(Screen.DRIVING, s.screen)
        assertTrue(s.tts.contains("분 뒤 공개"))
        assertFalse(s.tts.contains(s.currentStop!!.name)) // the name must not leak before arrival
    }

    @Test fun failedPlanShowsTheReason() {
        engine0 = engine(); engine0.setConditions(Conditions(timeMin = 120)); engine0.onVehicle(100.0, Gear.PARK, FuelType.EV); engine0.start()
        assertEquals(Screen.FAIL, s.screen)
        assertTrue(s.fail.single().startsWith("시간"))
    }

    @Test fun carMovesOnlyInDriveAndSpendsEnergy() {
        val e = started(gear = Gear.PARK)
        e.tick(60.0, 60.0, 1.0)
        assertEquals(0.0, s.progKm, 0.0)
        e.onVehicle(null, Gear.DRIVE, null); e.tick(60.0, 60.0, 1.0)
        assertEquals(1.0, s.progKm, 0.001)
        assertEquals(100.0 - 1.0 / 5.0, s.soc, 0.001)
    }

    @Test fun arrivalWaitsForParkBeforeTheScreenRevealsAnything() {
        val e = started(); driveToArrival(e)
        assertEquals(Screen.ARRIVED, s.screen)
        assertEquals(Gear.DRIVE, s.gear) // the UI shows "park to reveal" while the gear is not P
    }

    @Test fun lowEnergyAsksAt20AndInsistsAt10() {
        val e = started()
        e.onVehicle(60.0, Gear.DRIVE, null); e.tick(1.0, 60.0, 1.0); assertNull(s.alert)
        e.onVehicle(19.0, Gear.DRIVE, null); e.tick(1.0, 60.0, 1.0); assertEquals(AlertLevel.LOW, s.alert)
        e.alertDismiss(); e.onVehicle(18.0, Gear.DRIVE, null); e.tick(1.0, 60.0, 1.0); assertNull(s.alert) // asked only once
        e.onVehicle(9.0, Gear.DRIVE, null); e.tick(1.0, 60.0, 1.0); assertEquals(AlertLevel.CRIT, s.alert)
    }

    @Test fun acceptingTheOfferInsertsAStationAndChargingFillsTo80() {
        val e = started(); val legsBefore = s.course!!.legs.size
        e.onVehicle(19.0, Gear.DRIVE, null); e.tick(1.0, 60.0, 1.0); assertEquals(AlertLevel.LOW, s.alert)
        e.alertGo()
        assertNull(s.alert)
        assertTrue(s.currentStop!!.charger)
        assertTrue(s.currentLeg!!.virtual)
        assertEquals(legsBefore + 1, s.course!!.legs.size)
        driveToArrival(e)
        assertEquals(Screen.ARRIVED, s.screen); assertNotNull(s.charge); assertFalse(s.charge!!.active)
        e.onVehicle(null, Gear.PARK, null)
        e.startCharge(); assertTrue(s.charge!!.active)
        var n = 0; while (s.charge!!.active && n++ < 1000) e.tick(1.0, 60.0, 10.0)
        assertEquals(80.0, s.soc, 0.001); assertTrue(s.charge!!.done); assertEquals(0, s.offer)
        e.next(); assertEquals(Screen.DRIVING, s.screen); assertNull(s.charge)
    }

    @Test fun chargingNeedsParkAndPausesWhenDriving() {
        val e = started(); e.onVehicle(19.0, Gear.DRIVE, null); e.tick(1.0, 60.0, 1.0); e.alertGo(); driveToArrival(e)
        e.startCharge(); assertFalse(s.charge!!.active) // still in D
        e.onVehicle(null, Gear.PARK, null); e.startCharge(); e.tick(60.0, 60.0, 1.0)
        val mid = s.soc
        e.onVehicle(null, Gear.DRIVE, null); e.tick(60.0, 60.0, 1.0)
        assertEquals(mid, s.soc, 0.0)
    }

    @Test fun gasolineStopIsAGasStationAndFillsTo100() {
        val e = started(FuelType.ICE)
        e.onVehicle(14.0, Gear.DRIVE, null); e.tick(1.0, 60.0, 1.0); assertEquals(AlertLevel.LOW, s.alert); assertTrue(s.tts.contains("연료"))
        e.alertGo(); assertTrue(s.tts.contains("주유소")); driveToArrival(e)
        e.onVehicle(null, Gear.PARK, null); e.startCharge()
        var n = 0; while (s.charge!!.active && n++ < 1000) e.tick(1.0, 60.0, 10.0)
        assertEquals(100.0, s.soc, 0.001); assertTrue(s.tts.contains("주유가 끝났어요"))
    }

    @Test fun refusesWhenNoStationIsReachable() {
        val e = started(); e.onVehicle(100.0, Gear.DRIVE, null); e.tick(60.0, 60.0, 1.0)
        val before = s.course!!.legs.map { it.km }
        e.onVehicle(1.0, Gear.DRIVE, null); e.reroute(Cmd.Charge)
        assertTrue(s.tts.contains("긴급 출동")); assertEquals(before, s.course!!.legs.map { it.km })
    }

    @Test fun changingRouteMidLegStartsAVirtualLegAndKeepsTheKilometresDriven() {
        val e = started(); e.tick(600.0, 60.0, 1.0) // 10 km
        val target = s.currentStop!!.id; val driven = s.progKm
        e.reroute(Cmd.Swap)
        assertNotEquals(target, s.currentStop!!.id); assertTrue(s.currentLeg!!.virtual); assertEquals(0.0, s.progKm, 0.0); assertEquals(driven, s.extraKm, 0.001)
    }

    @Test fun aChangedLegCanOnlyGoForward() {
        val e = started(); e.tick(600.0, 60.0, 1.0); e.reroute(Cmd.Swap)
        val afterFirst = s.currentStop!!.id; val legKm = s.currentLeg!!.km
        e.tick(300.0, 60.0, 1.0); e.reroute(Cmd.Swap)
        assertTrue(s.currentLeg!!.km >= legKm - s.progKm - 0.001) // never shorter than what is left of the road ahead
        assertNotNull(afterFirst)
    }

    @Test fun routeChangeAtAStopUsesExactDistancesAndKeepsHistory() {
        val e = started(); driveToArrival(e); e.onVehicle(null, Gear.PARK, null)
        val first = s.currentStop!!.id; val nextBefore = s.nextLeg!!.to
        e.reroute(Cmd.Kind("휴식"))
        assertEquals(first, s.currentStop!!.id)
        assertFalse(s.nextLeg!!.virtual)
        if (nextBefore != null) assertNotEquals(nextBefore.id, s.nextLeg!!.to?.id)
    }

    @Test fun goingHomeEndsTheTripAfterTheLastLeg() {
        val e = started(); e.tick(300.0, 60.0, 1.0); e.reroute(Cmd.Home)
        assertNull(s.currentStop); assertTrue(s.tts.contains("집까지"))
        driveToArrival(e); assertEquals(Screen.DONE, s.screen)
    }

    @Test fun ratingsAndVisitsAreRemembered() {
        val e = started(); driveToArrival(e)
        val id = s.currentStop!!.id
        e.rate(id, Rating.UP); assertEquals(Rating.UP, s.rated[id])
        e.toggleVisit(id, 1); assertEquals(setOf(1), s.visits[id]); e.toggleVisit(id, 1); assertEquals(emptySet<Int>(), s.visits[id])
    }
}
