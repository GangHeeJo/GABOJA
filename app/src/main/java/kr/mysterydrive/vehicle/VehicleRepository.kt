package kr.mysterydrive.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Wraps the Pleos Vehicle SDK: reads the first values, then listens for changes.
 * [onChange] always runs on the main thread. Call [stop] when the screen goes away.
 */
class VehicleRepository(private val context: Context, private val onChange: (VehicleState) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var state = VehicleState()

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(p0: Int?) = setSoc(p0)
        override fun onFailed(p0: Exception) = fail("soc", p0)
    }
    private val fuelListener = object : FuelPercentListener {
        override fun onFuelPercentUpdated(p0: Int?) = setFuel(p0)
        override fun onFailed(p0: Exception) = fail("fuel", p0)
    }
    private val rangeListener = object : RangeRemainingListener {
        override fun onRangeRemainingChanged(p0: Float?) = setRange(p0)
        override fun onFailed(p0: Exception) = fail("range", p0)
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(p0: VehicleGear?) = setGear(p0)
        override fun onFailed(p0: Exception) = fail("gear", p0)
    }

    fun start() {
        if (vehicle != null) return
        val v = try {
            Vehicle(context).also { it.initialize() }
        } catch (e: Throwable) {
            fail("init", e); return
        }
        vehicle = v
        update { copy(connected = true) }
        // First values, then live updates. Each call can fail on its own (for example fuel on an EV).
        guard("soc") {
            v.getEvBattery().getEvBatteryStateOfCharge({ setSoc(it) }, { fail("soc", it) })
            v.getEvBattery().registerEvBatteryStateOfCharge(socListener)
        }
        guard("fuel") {
            v.getFuel().getFuelPercent({ setFuel(it) }, { fail("fuel", it) })
            v.getFuel().registerFuelPercent(fuelListener)
        }
        guard("range") {
            v.getOdometer().getRangeRemainingDistance({ setRange(it) }, { fail("range", it) })
            v.getOdometer().registerRangeRemainingListener(rangeListener)
        }
        guard("gear") {
            v.getGear().getCurrentGearSelectionState({ setGear(it) }, { fail("gear", it) })
            v.getGear().registerGearSelection(gearListener)
        }
    }

    fun stop() {
        val v = vehicle ?: return
        guard("stop") {
            v.getEvBattery().unregisterEvBatteryStateOfCharge(socListener)
            v.getOdometer().unregisterRangeRemainingListener(rangeListener)
            v.getGear().unregisterGearSelection(gearListener)
            v.getFuel().unregisterFuelPercent(fuelListener)
        }
        v.release()
        vehicle = null
    }

    private fun setSoc(v: Int?) = update { copy(socPercent = v?.toDouble(), errors = errors - "soc") }
    private fun setFuel(v: Int?) = update { copy(fuelPercent = v?.toDouble(), errors = errors - "fuel") }
    private fun setRange(meters: Float?) = update { copy(rangeKm = rangeKmFromMeters(meters), errors = errors - "range") }
    private fun setGear(g: VehicleGear?) = update { copy(gear = gearFromName(g?.name), errors = errors - "gear") }
    private fun fail(what: String, e: Throwable?) = update { copy(errors = errors + (what to (e?.message ?: e?.javaClass?.simpleName ?: "unknown"))) }

    private fun guard(what: String, block: () -> Unit) = try { block() } catch (e: Throwable) { fail(what, e) }

    private fun update(change: VehicleState.() -> VehicleState) = main.post {
        state = state.change()
        onChange(state)
    }.let { }
}
