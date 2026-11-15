package kr.mysterydrive.vehicle

enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** What the car tells us right now. Null means "not received yet" (or the car does not support it). */
data class VehicleState(
    val connected: Boolean = false,
    val socPercent: Double? = null,
    val fuelPercent: Double? = null, // gasoline cars: Fuel.getFuelPercent (the EV emulator reports an error here)
    val rangeKm: Double? = null,
    val gear: Gear = Gear.UNKNOWN,
    val errors: Map<String, String> = emptyMap(),
) {
    /** The screen may be touched only while parked. */
    val isParked get() = gear == Gear.PARK
}

/** The Pleos SDK reports the remaining range in metres (emulator: 500000.0 for a 500 km range). */
fun rangeKmFromMeters(meters: Float?): Double? = meters?.let { it / 1000.0 }

/** Maps the SDK's VehicleGear enum name (for example GEAR_PARK) to our own Gear. Numbered gears count as driving. */
fun gearFromName(name: String?): Gear = when {
    name == null -> Gear.UNKNOWN
    name.endsWith("PARK") -> Gear.PARK
    name.endsWith("REVERSE") -> Gear.REVERSE
    name.endsWith("NEUTRAL") -> Gear.NEUTRAL
    name.endsWith("DRIVE") || Regex("GEAR_\\d").matches(name) -> Gear.DRIVE
    else -> Gear.UNKNOWN
}
