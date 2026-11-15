package kr.mysterydrive.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kr.mysterydrive.vehicle.Gear
import java.io.File
import kotlin.math.hypot

class MapGeometryTest {
    private val roads = RoadData.fromJson(File("src/main/assets/roads.json").readText())
    private val geo = RouteGeometry.fromJson(File("src/main/assets/routes.json").readText())
    private val all = PLACES + HOME
    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)

    @Test fun decodesAKnownEncodedPolyline() { // the example from Google's polyline documentation
        val pts = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, pts.size)
        assertEquals(0.0, dist(pts[0], kmXY(38.5, -120.2)), 1e-6)
        assertEquals(0.0, dist(pts[2], kmXY(43.252, -126.453)), 1e-6)
    }

    @Test fun everyRoadStartsAndEndsAtItsPlaces() {
        for (a in all) for (b in all) {
            if (a.id == b.id || a.id !in roads.ids || b.id !in roads.ids) continue
            val p = geo.path(a, b)
            assertTrue("${a.id}->${b.id} has ${p.size} points", p.size >= 2)
            assertTrue("${a.id}->${b.id} start is ${dist(p.first(), kmXY(a))} km from ${a.id}", dist(p.first(), kmXY(a)) < 3.5)
            assertTrue("${a.id}->${b.id} end is ${dist(p.last(), kmXY(b))} km from ${b.id}", dist(p.last(), kmXY(b)) < 3.5)
        }
    }

    @Test fun theReverseRoadIsTheSameRoadBackwards() {
        val a = PLACES.first { it.id == "dmg" }
        assertEquals(geo.path(HOME, a), geo.path(a, HOME).reversed())
        assertTrue(geo.path(a, a).isEmpty())
    }

    @Test fun roadsFollowRealRoadsNotStraightLines() {
        val a = PLACES.first { it.id == "gg" } // Seoul to Gangneung is nowhere near a straight line
        val straight = dist(kmXY(HOME), kmXY(a))
        val road = lengthKm(geo.path(HOME, a))
        assertTrue("road $road km vs straight $straight km", road > straight && geo.path(HOME, a).size > 20)
    }

    @Test fun alongFindsPointAndHeading() {
        val line = listOf(Pt(0.0, 0.0), Pt(0.0, -10.0), Pt(10.0, -10.0)) // north, then east
        val start = along(line, 0.0)
        val mid = along(line, 0.25)
        val end = along(line, 1.0)
        assertEquals(0.0, dist(start.p, Pt(0.0, 0.0)), 1e-9)
        assertEquals(0.0, dist(mid.p, Pt(0.0, -5.0)), 1e-9); assertEquals(0.0, mid.headingDeg, 1e-6)  // heading north
        assertEquals(0.0, dist(end.p, Pt(10.0, -10.0)), 1e-9); assertEquals(90.0, end.headingDeg, 1e-6) // heading east
    }

    @Test fun tilesLineUpWithTheMap() {
        val seoul = kmXY(HOME)
        val nw = tileCorner(871, 393, 10)
        val se = tileCorner(880, 399, 10)
        assertTrue("Seoul should be inside the shipped tiles", seoul.x in nw.x..se.x && seoul.y in nw.y..se.y)
        for (p in PLACES) { val q = kmXY(p); assertTrue("${p.id} outside the tiles", q.x in nw.x..se.x && q.y in nw.y..se.y) }
    }

    private fun engine() = TripEngine(Planner(roads), Rerouter(roads), roads, geo) { 0.5 }

    @Test fun legsUseRealRoadsAndAChangedLegStartsWhereTheCarIs() {
        val e = engine()
        e.setConditions(Conditions(timeMin = 360, budget = 200_000, maxKm = 300)); e.onVehicle(100.0, Gear.DRIVE, FuelType.EV); e.start()
        val course = e.state.value.course!!
        assertTrue(e.legPoints(course, 0).size > 3)
        e.tick(600.0, 60.0, 1.0) // 10 km down the road
        val carAt = along(e.legPoints(course, 0), e.state.value.progKm / course.legs[0].km).p
        e.reroute(Cmd.Swap)
        val s = e.state.value
        val leg = s.currentLeg!!
        assertNotNull(leg.path); assertTrue(leg.virtual)
        assertEquals(1, s.trail.size)
        assertTrue("new road starts ${dist(leg.path!!.first(), carAt)} km from the car", dist(leg.path.first(), carAt) < 0.5 || dist(leg.path.last(), carAt) < 0.5 || leg.path.any { dist(it, carAt) < 0.5 })
        assertTrue("new road ends ${dist(leg.path.last(), kmXY(leg.to!!))} km from its destination", dist(leg.path.last(), kmXY(leg.to)) < 3.5)
    }

    @Test fun theMapRoadOfEveryLegOfAWholeTripIsContinuous() {
        val e = engine()
        e.setConditions(Conditions(timeMin = 360, budget = 200_000, maxKm = 300)); e.onVehicle(100.0, Gear.DRIVE, FuelType.EV); e.start()
        val c = e.state.value.course!!
        c.legs.indices.forEach { k ->
            val p = e.legPoints(c, k)
            if (k > 0) assertTrue("leg $k does not start where leg ${k - 1} ended", dist(e.legPoints(c, k - 1).last(), p.first()) < 4.0)
        }
    }
}
