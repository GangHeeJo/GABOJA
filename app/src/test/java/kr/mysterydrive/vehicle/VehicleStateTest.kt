package kr.mysterydrive.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStateTest {
    @Test fun rangeIsConvertedFromMetersToKm() {
        assertEquals(500.0, rangeKmFromMeters(500000.0f)!!, 0.001) // the value the emulator reported
        assertEquals(0.0, rangeKmFromMeters(0f)!!, 0.001)
        assertNull(rangeKmFromMeters(null))
    }

    @Test fun gearNamesMapToOurGears() {
        assertEquals(Gear.PARK, gearFromName("GEAR_PARK"))
        assertEquals(Gear.DRIVE, gearFromName("GEAR_DRIVE"))
        assertEquals(Gear.DRIVE, gearFromName("GEAR_3"))
        assertEquals(Gear.REVERSE, gearFromName("GEAR_REVERSE"))
        assertEquals(Gear.NEUTRAL, gearFromName("GEAR_NEUTRAL"))
        assertEquals(Gear.UNKNOWN, gearFromName("GEAR_UNKNOWN"))
        assertEquals(Gear.UNKNOWN, gearFromName(null))
    }

    @Test fun onlyParkAllowsTouch() {
        assertTrue(VehicleState(gear = Gear.PARK).isParked)
        assertFalse(VehicleState(gear = Gear.DRIVE).isParked)
        assertFalse(VehicleState().isParked) // unknown counts as not parked, so we stay voice-only
    }
}
