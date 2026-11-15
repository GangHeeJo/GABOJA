package kr.mysterydrive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kr.mysterydrive.core.AlertLevel
import kr.mysterydrive.core.Cmd
import kr.mysterydrive.core.FuelType
import kr.mysterydrive.core.KIND_LABEL
import kr.mysterydrive.core.Place
import kr.mysterydrive.core.Rating
import kr.mysterydrive.core.Screen
import kr.mysterydrive.core.Spot
import kr.mysterydrive.core.TripState
import kr.mysterydrive.core.photoCredit
import kr.mysterydrive.core.spotsFor
import kr.mysterydrive.vehicle.Gear

private val Pad = 24.dp

@Composable
fun MysteryApp(vm: TripViewModel) {
    val s by vm.trip.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(Pal.Bg)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(s, ui, vm)
            if (ui.demoOpen) DemoBar(s, ui, vm)
            Divider()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (s.screen) {
                    Screen.INPUT -> Centered { InputScreen(s, ui, vm) }
                    Screen.FAIL -> Centered { FailScreen(s, vm) }
                    else -> Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(0.46f).fillMaxHeight()) { key(s.screen, s.leg) { LeftPane(s, ui, vm) } }
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Pal.Line))
                        Box(Modifier.weight(0.54f).fillMaxHeight()) { RoutePanel(s) }
                    }
                }
                if (s.alert != null && s.screen == Screen.DRIVING) AlertCard(s, vm, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { Box(Modifier.widthIn(max = 920.dp).fillMaxHeight()) { content() } }
}

// ---------------------------------------------------------------- top bar and demo tools
@Composable
private fun TopBar(s: TripState, ui: UiState, vm: TripViewModel) {
    val parked = s.gear == Gear.PARK
    Row(Modifier.fillMaxWidth().padding(horizontal = Pad, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Txt(s.fuel.energy, 13f, color = Pal.Mute); Txt("${s.soc.roundToInt()}%", 13f, FontWeight.Bold) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Txt("주행가능", 13f, color = Pal.Mute); Txt("${s.rangeKm.roundToInt()} km", 13f, FontWeight.Bold) }
        Spacer(Modifier.weight(1f))
        GhostButton(if (ui.demoOpen) "데모 도구 접기" else "데모 도구", onClick = vm::toggleDemo)
        Box(Modifier.clip(RoundedCornerShape(50)).background(if (parked) Pal.Strong else Color(0xFFB4B4B4)).padding(horizontal = 12.dp, vertical = 5.dp)) {
            Txt(if (parked) "정차 모드" else "주행 모드 · 음성 안내", 12.5f, FontWeight.Bold, Pal.OnStrong)
        }
    }
}

/** The emulator has no road and no pump, so the gear, the clock and the fuel type can be driven from here. */
@Composable
private fun DemoBar(s: TripState, ui: UiState, vm: TripViewModel) {
    val gap = Arrangement.spacedBy(10.dp)
    Column(Modifier.fillMaxWidth().background(Pal.Card).padding(horizontal = Pad, vertical = 12.dp)) {
        Pills { // wraps onto a second line instead of squeezing the last button
            Row(horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
                Txt("기어", 12f, color = Pal.Mute)
                Segmented(listOf("CAR" to "차량값", "PARK" to "P", "DRIVE" to "D"), when (ui.gearOverride) { null -> "CAR"; Gear.PARK -> "PARK"; else -> "DRIVE" }) {
                    vm.setGear(when (it) { "PARK" -> Gear.PARK; "DRIVE" -> Gear.DRIVE; else -> null })
                }
            }
            Row(horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
                Txt("시간 배속", 12f, color = Pal.Mute)
                Segmented(listOf("1" to "x1", "100" to "x100", "400" to "x400"), ui.timeScale.roundToInt().toString()) { vm.setScale(it.toDouble()) }
            }
            Row(horizontalArrangement = gap, verticalAlignment = Alignment.CenterVertically) {
                Txt("차량", 12f, color = Pal.Mute)
                Segmented(listOf("EV" to "전기차", "ICE" to "내연기관"), s.fuel.name) { vm.setFuelType(FuelType.valueOf(it)) }
            }
            SecondaryButton("${s.fuel.energy} -10%", onClick = vm::lowerEnergy)
            if (s.screen == Screen.DRIVING) SecondaryButton("도착 처리", onClick = vm::arriveNow)
        }
    }
}

// ---------------------------------------------------------------- input and failure
@Composable
private fun InputScreen(s: TripState, ui: UiState, vm: TripViewModel) {
    val c = s.cond
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Pad), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("어디로 갈지는 정하지 마세요.", 28f, FontWeight.ExtraBold)
            Txt("조건만 말하면 코스를 만들고, 도착하기 전까지 목적지는 숨겨요.", 14f, color = Pal.Mute)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = ui.voiceText, onValueChange = vm::setVoiceText, singleLine = true,
                textStyle = TextStyle(color = Pal.Ink, fontSize = 15.sp, fontFamily = LocalAppFont.current), cursorBrush = SolidColor(Pal.Ink),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Pal.Card).border(BorderStroke(1.dp, Pal.Line), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 13.dp),
            )
            SecondaryButton("음성으로 말하기", onClick = vm::speakConditions)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Divider(); Spacer(Modifier.height(4.dp)); GroupLabel("여행 시간")
            Pills { listOf(120 to "2시간", 240 to "4시간", 360 to "반나절", 1200 to "1박 2일").forEach { (v, l) -> Pill(l, c.timeMin == v) { vm.setConditions(c.copy(timeMin = v)) } } }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Divider(); Spacer(Modifier.height(4.dp)); GroupLabel("분위기")
            Pills { listOf("랜덤", "자연", "맛집", "휴식", "문화").forEach { m -> Pill(m, c.mood == m) { vm.setConditions(c.copy(mood = m)) } } }
        }
        Accordion("세부 조건", "예산 ${c.budget / 10_000}만 원 · 최대 ${c.maxKm}km", "adv" in ui.open, { vm.toggle("adv") }) {
            GroupLabel("예산")
            Pills { listOf(50_000 to "5만 원", 100_000 to "10만 원", 200_000 to "20만 원").forEach { (v, l) -> Pill(l, c.budget == v) { vm.setConditions(c.copy(budget = v)) } } }
            GroupLabel("최대 주행 거리")
            Pills { listOf(80 to "80 km", 150 to "150 km", 300 to "300 km").forEach { (v, l) -> Pill(l, c.maxKm == v) { vm.setConditions(c.copy(maxKm = v)) } } }
        }
        Txt("시간은 보수적으로 잡아요: 이동 +15%, 장소마다 주차·이동 10분, 전체의 10%는 예비로 남겨요.", 12.5f, color = Pal.Mute)
        PrimaryButton("아무 데나 데려가 줘   →", onClick = vm::start)
    }
}

@Composable
private fun FailScreen(s: TripState, vm: TripViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Pad), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Txt("이 조건으로는 갈 수 있는 곳이 없어요.", 26f, FontWeight.ExtraBold)
        Divider(); GroupLabel("이유")
        s.fail.forEach { Card { Txt(it, 15f) } }
        PrimaryButton("조건 다시 정하기", onClick = vm::reset)
    }
}

// ---------------------------------------------------------------- the left pane while on the trip
@Composable
private fun LeftPane(s: TripState, ui: UiState, vm: TripViewModel) {
    when (s.screen) {
        Screen.DRIVING -> DrivingPane(s, ui, vm)
        Screen.DONE -> DonePane(s, vm)
        Screen.ARRIVED -> {
            val stop = s.currentStop
            when {
                stop == null -> Unit
                s.gear != Gear.PARK -> WaitForPark(s)
                stop.charger -> ChargePane(s, ui, vm, stop)
                else -> PlacePane(s, ui, vm, stop)
            }
        }
        else -> Unit
    }
}

@Composable
private fun DrivingPane(s: TripState, ui: UiState, vm: TripViewModel) {
    val leg = s.currentLeg ?: return
    val mins = max(1, ceil((leg.km - s.progKm) / 50.0 * 60).toInt())
    val frac = if (leg.km > 0) (s.progKm / leg.km).toFloat().coerceIn(0f, 1f) else 1f
    val moving = s.gear == Gear.DRIVE
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Pad), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GroupLabel(if (leg.to != null) "${s.leg + 1}번째 장소까지" else "귀가까지")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Txt("$mins", 56f, FontWeight.ExtraBold, lineHeight = 60f); Txt("분", 16f, color = Pal.Mute, modifier = Modifier.padding(bottom = 10.dp)) }
            ProgressBar(frac)
        }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { GroupLabel("오늘의 힌트"); Txt(leg.to?.hint ?: "집으로 돌아가는 길", 18f, FontWeight.Bold) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { GroupLabel("음성 안내"); Txt(s.tts, 15f) }
        Txt(if (moving) "화면 조작은 정차 후에 할 수 있어요." else "기어가 P예요. D로 바꾸면 출발합니다.", 13f, if (moving) FontWeight.Normal else FontWeight.Bold, if (moving) Pal.Mute else Pal.Ink)
        VoiceCommands(s, ui, vm)
    }
}

@Composable
private fun WaitForPark(s: TripState) {
    Column(Modifier.fillMaxSize().padding(Pad), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Txt("도착했어요.", 28f, FontWeight.ExtraBold)
        Card { Txt("안전하게 정차(P)하면 어디인지 알려 드릴게요.", 17f, FontWeight.Bold) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { GroupLabel("음성 안내"); Txt(s.tts, 15f) }
    }
}

// ---------------------------------------------------------------- a place: summary first, details on demand
@Composable
private fun PlacePane(s: TripState, ui: UiState, vm: TripViewModel, p: Place) {
    val t = vm.tour[p.id]
    val nxt = s.nextLeg
    val spots = remember(p.id) { spotsFor(p.id, vm.tour) }
    val extra = (s.visits[p.id].orEmpty()).sumOf { spots.getOrNull(it)?.minutes ?: 0 }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Pad).padding(top = Pad, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(p.kind); Txt("${s.leg + 1} / ${s.course?.stops?.size ?: 0}번째 장소", 12.5f, color = Pal.Mute)
            }
            Txt(p.label(s.fuel), 28f, FontWeight.ExtraBold, lineHeight = 34f)
            if (t != null && t.photos.isNotEmpty()) PhotoBlock(t.photos.map { it.path to it.caption }, ui.photo, vm::setPhoto, p.name)
            Facts(listOf(
                "${p.stay + extra}분" to ("예상 관광 시간" + (if (p.stay < p.rec) " (권장 ${p.rec}분)" else "") + (if (extra > 0) " · 들르는 곳 +${extra}분" else "")),
                (if (p.cost > 0) "%,d원".format(p.cost) else "무료") to "예상 비용",
                (nxt?.let { "${it.min}분" } ?: "") to (if (nxt?.to != null) "다음 장소까지" else "귀가까지"),
            ))
            Txt(p.act, 15f, FontWeight.Bold)
            val overview = t?.overview?.ifBlank { null } ?: p.about
            if (overview != null) {
                val long = overview.length > 80
                val open = "overview" in ui.open
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt(overview, 14.5f, color = Color(0xFFD4D4D4), maxLines = if (long && !open) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis, lineHeight = 22f)
                    if (long) MoreButton("더보기", open) { vm.toggle("overview") }
                    if (open || !long) t?.let { Txt("출처: ${it.overviewSource}", 11.5f, color = Pal.Mute) }
                }
            }
            if (t != null) {
                val usage = listOf(t.usetime to "이용 시간", t.restdate to "휴무", t.parking to "주차", t.tel to "문의")
                if (usage.any { it.first.isNotBlank() }) {
                    Accordion("이용 정보", listOf(t.usetime, t.restdate).filter { it.isNotBlank() }.joinToString(" · ").take(40), "usage" in ui.open, { vm.toggle("usage") }) { Facts(usage, perRow = 2) }
                }
            }
            NearSection(s, ui, vm, p, spots, charger = false)
            RatingSection(s, vm, p)
            VoiceCommands(s, ui, vm)
        }
        Cta(Modifier.align(Alignment.BottomCenter)) {
            PrimaryButton((if (nxt?.to != null) "다음 장소로 출발" else "집으로 출발") + "   →", onClick = vm::next)
            Txt("출발 후 다음 힌트가 다시 숨겨져요.", 12f, color = Pal.Mute, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A photo you can tap to see the next one, with its credit underneath. */
@Composable
private fun PhotoBlock(photos: List<Pair<String, String>>, index: Int, onIndex: (Int) -> Unit, name: String) {
    val i = index.coerceIn(0, photos.size - 1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = photos.size > 1) { onIndex((i + 1) % photos.size) }) {
            AssetImage(photos[i].first, Modifier.fillMaxSize(), name)
            if (photos.size > 1) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 3.dp)) { Txt("${i + 1} / ${photos.size}", 11.5f, color = Color.White) }
        }
        Txt(photos[i].second, 10.5f, color = Pal.Mute)
    }
}

@Composable
private fun Cta(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x000F0F0F), Pal.Bg, Pal.Bg))).padding(start = Pad, end = Pad, top = 28.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

// ---------------------------------------------------------------- nearby: folded, grouped, three per group
@Composable
private fun NearSection(s: TripState, ui: UiState, vm: TripViewModel, p: Place, spots: List<Spot>, charger: Boolean) {
    val sel = ui.spot?.takeIf { it in spots.indices }
    if (sel != null) { SpotDetail(s, ui, vm, p, spots, sel); return }
    val counts = listOf("맛집", "카페", "사진").map { t -> t to spots.count { it.type == t } }.filter { it.second > 0 }
    val title = if (charger) (if (s.fuel == FuelType.EV) "충전하는 동안 둘러보기" else "쉬는 동안 둘러보기") else "근처 둘러보기"
    Accordion(title, counts.joinToString(" · ") { "${KIND_LABEL[it.first]} ${it.second}" }.ifEmpty { "없음" }, "near" in ui.open, { vm.toggle("near") }) {
        Txt(if (spots.firstOrNull()?.real == true) "한국관광공사 TourAPI" else "샘플 데이터", 11.5f, color = Pal.Mute)
        Segmented(listOf("전체" to "전체", "맛집" to "맛집", "카페" to "카페", "사진" to "명소"), ui.near, onSelect = vm::setNear)
        val types = if (ui.near == "전체") listOf("맛집", "카페", "사진") else listOf(ui.near)
        val groups = types.map { t -> t to spots.withIndex().filter { it.value.type == t } }.filter { it.second.isNotEmpty() }
        if (groups.isEmpty()) Txt("이 분류의 장소가 없어요.", 14f, color = Pal.Mute)
        groups.forEach { (t, rows) ->
            val all = "near_$t" in ui.open
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag(t); Txt((when (t) { "맛집" -> "식당"; "카페" -> "카페"; else -> "가볼 만한 곳" }) + " · ${rows.size}", 12.5f, color = Pal.Mute)
                }
                (if (all) rows else rows.take(3)).forEach { (i, sp) -> SpotRow(sp, visited = i in s.visits[p.id].orEmpty()) { vm.openSpot(i) } }
                if (rows.size > 3) MoreButton("더보기 (+${rows.size - 3})", all) { vm.toggle("near_$t") }
            }
        }
    }
}

@Composable
private fun SpotRow(sp: Spot, visited: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.Card).border(BorderStroke(1.dp, Pal.Line), RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (sp.photoPath != null) AssetImage(sp.photoPath, Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)))
        else Box(Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A)), contentAlignment = Alignment.Center) { Box(Modifier.size(14.dp).background(Pal.Faint, CircleShape)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(sp.name, 15f, FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Txt(listOf(sp.note, if (sp.real) sp.hours else "").filter { it.isNotBlank() }.joinToString(" · "), 12f, color = Pal.Mute, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (visited) Box(Modifier.clip(RoundedCornerShape(50)).background(Pal.Strong).padding(horizontal = 8.dp, vertical = 2.dp)) { Txt("들르는 중", 11f, FontWeight.Bold, Pal.OnStrong) }
        Txt("›", 20f, color = Pal.Mute)
    }
}

@Composable
private fun SpotDetail(s: TripState, ui: UiState, vm: TripViewModel, p: Place, spots: List<Spot>, index: Int) {
    val sp = spots[index]
    val added = index in s.visits[p.id].orEmpty()
    val hours = if (sp.real) sp.hours else when (sp.type) { "맛집" -> "11:00 – 21:00"; "카페" -> "09:00 – 22:00"; else -> "상시 개방" }
    val longDesc = sp.desc.length > 70
    val openDesc = "spotdesc" in ui.open
    val detail = listOf(sp.rest to "휴무", sp.parking to "주차", sp.tel to "전화", sp.addr to "주소")
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Divider()
        GhostButton("← 목록으로") { vm.openSpot(null) }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { Tag(sp.type); Txt(if (sp.real) "한국관광공사 TourAPI" else "샘플 데이터", 11.5f, color = Pal.Mute) }
                Txt(sp.name, 20f, FontWeight.ExtraBold, lineHeight = 26f)
                if (sp.photoPath != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AssetImage(sp.photoPath, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp)), sp.name)
                        sp.caption?.let { Txt(it, 10.5f, color = Pal.Mute) }
                    }
                } else Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(BorderStroke(1.dp, Pal.Line), RoundedCornerShape(10.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                    Txt(if (sp.real) "이 장소는 사진이 등록돼 있지 않아요." else "샘플 데이터라 사진이 없어요.", 13f, color = Pal.Mute)
                }
                if (sp.desc.isNotBlank()) {
                    Txt(sp.desc, 14f, color = Color(0xFFD4D4D4), maxLines = if (longDesc && !openDesc) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis, lineHeight = 21f)
                    if (longDesc) MoreButton("더보기", openDesc) { vm.toggle("spotdesc") }
                }
                Facts(listOf(sp.note to "위치·이동", "${sp.minutes}분" to ("예상 소요 시간" + if (sp.real) " (추정)" else ""), hours to ("이용 시간" + if (sp.real) "" else " (샘플)")))
                if (detail.any { it.first.isNotBlank() }) {
                    MoreButton("상세 정보 보기", "spotmore" in ui.open) { vm.toggle("spotmore") }
                    if ("spotmore" in ui.open) Facts(detail, perRow = 2)
                }
            }
        }
        SecondaryButton(if (added) "✓ 들르는 중 · 눌러서 취소" else "여기도 들를게요 (+${sp.minutes}분)", Modifier.fillMaxWidth(), on = added) { vm.toggleVisit(p.id, index) }
    }
}

@Composable
private fun RatingSection(s: TripState, vm: TripViewModel, p: Place) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Divider(); Spacer(Modifier.height(2.dp)); GroupLabel("이 장소는 어땠나요?")
        val r = s.rated[p.id]
        Segmented(listOf("UP" to "좋았어요", "DOWN" to "별로예요"), r?.name ?: "", Modifier.fillMaxWidth(), equalWidth = true) { vm.rate(p.id, Rating.valueOf(it)) }
    }
}

@Composable
private fun VoiceCommands(s: TripState, ui: UiState, vm: TripViewModel) {
    val cmds = listOf<Pair<Cmd, String>>(
        Cmd.Swap to "다음 장소 바꿔줘", Cmd.Skip to "다음 장소는 건너뛰어줘", Cmd.Kind("휴식") to "조금 쉬고 싶어",
        Cmd.Kind("문화") to "비가 와, 실내로 가자", Cmd.Home to "그만 집에 갈래", Cmd.Charge to "근처 ${s.fuel.station} 안내해줘",
    )
    Accordion("음성 명령", if (s.gear == Gear.DRIVE) "주행 중에도 사용" else "말해서 경로 바꾸기", "cmd" in ui.open, { vm.toggle("cmd") }) {
        Pills { cmds.forEach { (c, label) -> Pill("“$label”", selected = false, soft = true) { vm.command(c) } } }
    }
}

// ---------------------------------------------------------------- charging / refuelling
@Composable
private fun ChargePane(s: TripState, ui: UiState, vm: TripViewModel, p: Place) {
    val c = s.charge ?: return
    val ev = s.fuel == FuelType.EV
    val word = if (ev) "충전" else "주유"
    val left = max(0, ceil((c.target - s.soc) / s.fuel.ratePctPerMin).toInt())
    val added = max(0.0, s.soc - c.startPct)
    val cost = (added * (if (ev) 0.774 * 340 else 0.55 * 1650)).roundToInt() // 77.4 kWh x 340 won, or 55 L x 1,650 won (sample prices)
    val status = when { c.done -> "$word 완료"; c.active -> "${left}분 남음"; s.gear != Gear.PARK -> "정차(P)하면 시작할 수 있어요"; else -> "$word 대기 중" }
    val nxt = s.nextLeg
    val spots = remember(p.id) { spotsFor(p.id, vm.tour) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Pad).padding(top = Pad, bottom = 130.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Tag("충전"); Txt("${s.leg + 1} / ${s.course?.stops?.size ?: 0}번째 정차", 12.5f, color = Pal.Mute) }
            Txt(p.label(s.fuel), 28f, FontWeight.ExtraBold)
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                        GroupLabel(s.fuel.energy); Txt("${s.soc.roundToInt()}%", 40f, FontWeight.ExtraBold, lineHeight = 42f)
                    }
                    ProgressBar((s.soc / 100).toFloat().coerceIn(0f, 1f), marker = c.target / 100f)
                    Txt("$status · 목표 ${c.target}% · 주행가능 ${s.rangeKm.roundToInt()}km", 13f, color = Pal.Mute)
                }
            }
            Facts(listOf((if (c.active || c.done) "%,d원".format(cost) else "-") to "예상 요금 (샘플 단가)", (if (ev) "급속 150kW" else "휴게소 주유기") to "설비", (nxt?.let { "${it.min}분" } ?: "") to "다음 장소까지"))
            NearSection(s, ui, vm, p, spots, charger = true)
            VoiceCommands(s, ui, vm)
        }
        Cta(Modifier.align(Alignment.BottomCenter)) {
            when {
                c.done -> PrimaryButton((if (nxt?.to != null) "다음 장소로 출발" else "집으로 출발") + "   →", onClick = vm::next)
                c.active -> { PrimaryButton("$word 중…  ${s.soc.roundToInt()}%", enabled = false) {}; GhostButton("중단하고 출발", Modifier.align(Alignment.CenterHorizontally), onClick = vm::next) }
                else -> { PrimaryButton("$word 시작", enabled = s.gear == Gear.PARK, onClick = vm::startCharge); GhostButton("$word 건너뛰고 출발", Modifier.align(Alignment.CenterHorizontally), onClick = vm::next) }
            }
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, marker: Float? = null) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Pal.Line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Pal.Strong))
        if (marker != null) {
            val m = marker.coerceIn(0.02f, 0.98f)
            Row(Modifier.fillMaxSize()) { Spacer(Modifier.weight(m)); Box(Modifier.width(2.dp).fillMaxHeight().background(Color.White)); Spacer(Modifier.weight(1f - m)) }
        }
    }
}

// ---------------------------------------------------------------- done
@Composable
private fun DonePane(s: TripState, vm: TripViewModel) {
    val c = s.course ?: return
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Pad).padding(top = Pad, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Txt("오늘의 여행 기록", 28f, FontWeight.ExtraBold)
            Divider(); GroupLabel("다녀온 곳 · ${c.stops.size}곳")
            c.stops.forEachIndexed { i, p ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.Card).border(BorderStroke(1.dp, Pal.Line), RoundedCornerShape(12.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(26.dp).background(Pal.Strong, CircleShape), contentAlignment = Alignment.Center) { Txt("${i + 1}", 13f, FontWeight.Bold, Pal.OnStrong) }
                    Txt(p.label(s.fuel), 15f, FontWeight.Bold, modifier = Modifier.weight(1f))
                    Tag(p.kind)
                    Txt(when (s.rated[p.id]) { Rating.UP -> "좋았어요"; Rating.DOWN -> "별로예요"; null -> "" }, 12.5f, color = Pal.Mute)
                }
            }
            Facts(listOf("${c.totalKm.roundToInt()}km" to "총 주행", "${(c.totalMin / 6.0).roundToInt() / 10.0}시간" to "총 소요", "${s.soc.roundToInt()}%" to "남은 ${s.fuel.energy}"))
        }
        Cta(Modifier.align(Alignment.BottomCenter)) { PrimaryButton("새 여행 시작", onClick = vm::reset) }
    }
}

// ---------------------------------------------------------------- right pane: the trip so far (the map itself is the car's navigation)
@Composable
private fun RoutePanel(s: TripState) {
    val c = s.course ?: return
    val legs = c.legs
    Column(Modifier.fillMaxSize().padding(Pad), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GroupLabel("코스 진행")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            RouteRow("서울 · 출발", RowState.DONE, last = false)
            legs.forEachIndexed { i, l ->
                val stop = l.to
                val state = when { s.screen == Screen.DONE || i < s.leg -> RowState.DONE; i == s.leg -> RowState.NOW; else -> RowState.LATER }
                val shown = s.screen == Screen.DONE || i < s.leg || (i == s.leg && s.screen == Screen.ARRIVED && s.gear == Gear.PARK)
                if (stop != null) {
                    val label = if (shown) stop.label(s.fuel) else "?   숨겨진 장소"
                    val sub = if (i == s.leg && s.screen == Screen.DRIVING && l.km > 0) "이동 중 ${(s.progKm / l.km * 100).roundToInt()}%  ·  ${l.km.roundToInt()}km" else if (shown) "${l.min}분 이동" else ""
                    RouteRow(label, state, sub = sub, last = false)
                } else {
                    RouteRow("서울 · 귀가", if (s.screen == Screen.DONE) RowState.DONE else if (i == s.leg) RowState.NOW else RowState.LATER, sub = if (i == s.leg && s.screen == Screen.DRIVING) "이동 중 ${(s.progKm / max(l.km, 0.1) * 100).roundToInt()}%" else "", last = true)
                }
            }
        }
        Card { Txt("길 안내와 지도는 차량 내비게이션이 맡아요(NaviHelper로 목적지를 전달). 이 앱은 힌트와 장소 이야기를 보여 줍니다.", 12.5f, color = Pal.Mute) }
    }
}

private enum class RowState { DONE, NOW, LATER }

@Composable
private fun RouteRow(label: String, state: RowState, sub: String = "", last: Boolean) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.width(18.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.padding(top = 4.dp).size(16.dp).clip(CircleShape)
                    .background(if (state == RowState.DONE) Pal.Strong else Color.Transparent)
                    .border(BorderStroke(if (state == RowState.NOW) 3.dp else 1.5.dp, if (state == RowState.LATER) Pal.Faint else Pal.Strong), CircleShape),
            )
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(if (state == RowState.DONE) Pal.Mute else Pal.Line))
        }
        Column(Modifier.padding(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(label, 16f, if (state == RowState.NOW) FontWeight.ExtraBold else FontWeight.Bold, if (state == RowState.LATER) Pal.Mute else Pal.Ink)
            if (sub.isNotBlank()) Txt(sub, 12.5f, color = Pal.Mute)
        }
    }
}

// ---------------------------------------------------------------- low energy offer
@Composable
private fun AlertCard(s: TripState, vm: TripViewModel, modifier: Modifier) {
    val crit = s.alert == AlertLevel.CRIT
    Box(modifier.fillMaxWidth().padding(horizontal = Pad, vertical = 14.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1F1F1F)).border(BorderStroke(if (crit) 2.dp else 1.dp, if (crit) Color.White else Color(0xFFBDBDBD)), RoundedCornerShape(16.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                Txt("${s.fuel.energy} ${s.soc.roundToInt()}%", 22f, FontWeight.ExtraBold); Txt("주행가능 ${s.rangeKm.roundToInt()}km", 13f, color = Pal.Mute)
            }
            Txt((if (crit) "곧 멈출 수 있어요. 지금 가까운 ${s.fuel.station}로 안내할게요." else "가까운 ${s.fuel.station}로 안내할까요?") + " 음성으로 “${s.fuel.station} 안내해줘”라고 말해도 돼요.", 14f, color = Color(0xFFD4D4D4))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PrimaryButton("${s.fuel.station}로 안내   →", onClick = vm::alertGo) }
                if (!crit) GhostButton("나중에", onClick = vm::alertDismiss)
            }
        }
    }
}
