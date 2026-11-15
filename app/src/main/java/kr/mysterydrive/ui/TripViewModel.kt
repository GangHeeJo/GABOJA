package kr.mysterydrive.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kr.mysterydrive.core.Cmd
import kr.mysterydrive.core.Conditions
import kr.mysterydrive.core.FuelType
import kr.mysterydrive.core.Planner
import kr.mysterydrive.core.Rating
import kr.mysterydrive.core.Rerouter
import kr.mysterydrive.core.RoadData
import kr.mysterydrive.core.TourData
import kr.mysterydrive.core.TripEngine
import kr.mysterydrive.core.TripState
import kr.mysterydrive.core.merge
import kr.mysterydrive.core.parseCmd
import kr.mysterydrive.core.parseVoice
import kr.mysterydrive.vehicle.Gear
import kr.mysterydrive.vehicle.VehicleRepository
import kr.mysterydrive.vehicle.VehicleState

/** Things only the screen cares about: which folds are open, which photo, which spot, the demo tools. */
data class UiState(
    val open: Set<String> = emptySet(),
    val near: String = "전체",
    val photo: Int = 0,
    val spot: Int? = null,
    val voiceText: String = "오늘 4시간 안에, 10만 원 이하로, 맛있는 점심 포함해서 아무 데나 데려가 줘",
    val demoOpen: Boolean = true, // the emulator has no road, so the demo tools are open from the start
    val gearOverride: Gear? = null,
    val timeScale: Double = 100.0,
    val vehicle: VehicleState = VehicleState(),
)

class TripViewModel(app: Application) : AndroidViewModel(app) {
    private val roads = RoadData.fromJson(app.assets.open("roads.json").bufferedReader().use { it.readText() })
    val tour = TourData.fromJson(app.assets.open("tour.json").bufferedReader().use { it.readText() })
    private val engine = TripEngine(Planner(roads), Rerouter(roads), roads)

    val trip: StateFlow<TripState> = engine.state
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var lastEnergy: Double? = null
    private val repo = VehicleRepository(app) { v -> _ui.update { it.copy(vehicle = v) }; pushVehicle(v) }

    init {
        repo.start()
        // A new stop or screen starts compact again.
        viewModelScope.launch {
            trip.map { it.screen to it.leg }.distinctUntilChanged().collect { _ui.update { u -> u.copy(open = emptySet(), near = "전체", photo = 0, spot = null) } }
        }
        // The emulator cannot drive, so while the gear is D the trip moves on by itself (speed 60 km/h x time scale).
        viewModelScope.launch {
            while (isActive) { delay(250); engine.tick(0.25, 60.0, _ui.value.timeScale) }
        }
    }

    /** Car values go in. The energy value is only taken when it changed, so the trip's own spending is not overwritten. */
    private fun pushVehicle(v: VehicleState) {
        val energy = v.socPercent ?: v.fuelPercent
        val changed = energy != null && energy != lastEnergy
        if (energy != null) lastEnergy = energy
        val type = when { v.socPercent != null -> FuelType.EV; v.fuelPercent != null -> FuelType.ICE; else -> null }
        engine.onVehicle(if (changed) energy else null, _ui.value.gearOverride ?: v.gear, type)
    }

    // ---- screen state
    fun toggle(key: String) = _ui.update { it.copy(open = if (key in it.open) it.open - key else it.open + key) }
    fun setNear(k: String) = _ui.update { it.copy(near = k) }
    fun setPhoto(i: Int) = _ui.update { it.copy(photo = i) }
    fun openSpot(i: Int?) = _ui.update { it.copy(spot = i) }
    fun setVoiceText(t: String) = _ui.update { it.copy(voiceText = t) }

    // ---- trip
    fun setConditions(c: Conditions) = engine.setConditions(c)
    fun speakConditions() { val s = trip.value; engine.setConditions(s.cond.merge(parseVoice(_ui.value.voiceText))) }
    fun start() = engine.start()
    fun next() = engine.next()
    fun reset() = engine.reset()
    fun rate(placeId: String, r: Rating) = engine.rate(placeId, r)
    fun toggleVisit(placeId: String, i: Int) = engine.toggleVisit(placeId, i)
    fun startCharge() = engine.startCharge()
    fun alertGo() = engine.alertGo()
    fun alertDismiss() = engine.alertDismiss()
    fun command(cmd: Cmd) = engine.reroute(cmd)
    fun say(text: String) { parseCmd(text)?.let(engine::reroute) }

    // ---- demo tools (the emulator has no road and no pump)
    fun toggleDemo() = _ui.update { it.copy(demoOpen = !it.demoOpen) }
    fun setGear(g: Gear?) { _ui.update { it.copy(gearOverride = g) }; pushVehicle(_ui.value.vehicle) }
    fun setScale(x: Double) = _ui.update { it.copy(timeScale = x) }
    fun arriveNow() = engine.arrive()
    fun lowerEnergy() { val s = trip.value; engine.onVehicle(maxOf(0.0, s.soc - 10), s.gear, null) }
    fun setFuelType(f: FuelType) { val s = trip.value; engine.onVehicle(null, s.gear, f) }

    override fun onCleared() { repo.stop() }
}
