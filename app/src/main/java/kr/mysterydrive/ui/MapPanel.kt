package kr.mysterydrive.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.mysterydrive.core.HOME
import kr.mysterydrive.core.Pt
import kr.mysterydrive.core.RouteGeometry
import kr.mysterydrive.core.Screen
import kr.mysterydrive.core.TripState
import kr.mysterydrive.core.along
import kr.mysterydrive.core.kmXY
import kr.mysterydrive.core.tileCorner
import kr.mysterydrive.vehicle.Gear
import kotlin.math.hypot
import kotlin.math.max

private const val Z = 10
private val TILE_X = 871..879
private val TILE_Y = 393..398

/** The 54 OpenStreetMap tiles that cover Seoul to Gangneung, shipped inside the app (assets/map/10/x/y.png). */
@Composable
private fun rememberTiles(): Map<Pair<Int, Int>, ImageBitmap> {
    val context = LocalContext.current
    val tiles by produceState<Map<Pair<Int, Int>, ImageBitmap>>(emptyMap()) {
        value = withContext(Dispatchers.IO) {
            buildMap {
                for (x in TILE_X) for (y in TILE_Y) {
                    runCatching { context.assets.open("map/$Z/$x/$y.png").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()?.let { put(x to y, it) }
                }
            }
        }
    }
    return tiles
}

/**
 * The trip on a real map: roads already driven (black, white edge), a dotted hint of the direction ahead,
 * the places already revealed, and the car. Where the car is going stays hidden until it has arrived and parked.
 */
@Composable
fun MapPanel(s: TripState, geometry: RouteGeometry?, modifier: Modifier = Modifier) {
    val c = s.course ?: return
    val tiles = rememberTiles()
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111111), fontFamily = LocalAppFont.current)
    val small = TextStyle(fontSize = 10.sp, color = Color(0xFF333333), fontFamily = LocalAppFont.current)

    fun roads(k: Int): List<Pt> {
        val leg = c.legs[k]
        leg.path?.let { if (it.size >= 2) return it }
        val from = if (k == 0) HOME else c.legs[k - 1].to ?: HOME
        val to = leg.to ?: HOME
        return geometry?.path(from, to)?.takeIf { it.size >= 2 } ?: listOf(kmXY(from), kmXY(to))
    }

    Box(modifier.fillMaxSize().clipToBounds().background(Color(0xFFE8E4DC))) { // clip: the tiles are bigger than the pane
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val done = s.screen == Screen.DONE
            val leg = s.leg.coerceIn(0, c.legs.size - 1)
            val cur = roads(leg)
            val now = along(cur, if (c.legs[leg].km > 0) s.progKm / c.legs[leg].km else 1.0)

            // what to draw
            val finished: List<List<Pt>> = s.trail + (0 until (if (done) c.legs.size else leg)).map { roads(it) }
            val partial: List<Pt> = if (done) emptyList() else cur.take(now.k) + now.p
            val revealed = c.legs.take(if (done) c.legs.size else leg + 1).mapIndexedNotNull { i, l ->
                val shown = done || i < leg || (i == leg && s.screen == Screen.ARRIVED && s.gear == Gear.PARK)
                l.to.takeIf { shown }
            }

            // the window onto the map: 100 km wide around the car, or the whole trip once it is over
            var wKm = 100.0
            var cx = now.p.x
            var cy = now.p.y
            if (done) {
                val all = finished.flatten() + kmXY(HOME)
                val x0 = all.minOf { it.x } - 12
                val x1 = all.maxOf { it.x } + 30
                val y0 = all.minOf { it.y } - 10
                val y1 = all.maxOf { it.y } + 10
                wKm = max(x1 - x0, (y1 - y0) * w / h)
                cx = (x0 + x1) / 2
                cy = (y0 + y1) / 2
            }
            val scale = (w / wKm).toFloat()
            val ox = cx - wKm / 2
            val oy = cy - (h / scale) / 2
            fun px(p: Pt) = Offset(((p.x - ox) * scale).toFloat(), ((p.y - oy) * scale).toFloat())

            // tiles
            for ((key, img) in tiles) {
                val a = px(tileCorner(key.first, key.second, Z))
                val b = px(tileCorner(key.first + 1, key.second + 1, Z))
                drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), IntOffset(a.x.toInt(), a.y.toInt()), IntSize((b.x - a.x).toInt() + 1, (b.y - a.y).toInt() + 1))
            }

            // roads
            fun line(pts: List<Pt>, color: Color, width: Float, dashed: Boolean = false) {
                if (pts.size < 2) return
                val path = Path().apply { pts.forEachIndexed { i, p -> val o = px(p); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(width * 0.6f, width * 2.2f)) else null))
            }
            val edge = 9.dp.toPx()
            val core = 5.dp.toPx()
            (finished + listOf(partial)).forEach { line(it, Color.White, edge); line(it, Color(0xFF111111), core) }
            if (!done && s.screen == Screen.DRIVING) {
                var run = 0.0
                var prev = now.p
                val ahead = mutableListOf(now.p)
                for (p in cur.drop(now.k)) { if (run >= 25) break; run += hypot(p.x - prev.x, p.y - prev.y); ahead += p; prev = p }
                line(ahead, Color(0xFF111111), 4.dp.toPx(), dashed = true)
            }

            // places already revealed, and home
            fun pin(p: Pt, text: String, fill: Color) {
                val o = px(p)
                drawCircle(Color.White, 8.dp.toPx(), o)
                drawCircle(fill, 5.5.dp.toPx(), o)
                val m = measurer.measure(text, label)
                val tl = Offset(o.x + 12.dp.toPx(), o.y - m.size.height / 2f)
                drawRoundRect(Color(0xE6FFFFFF), tl - Offset(5.dp.toPx(), 2.dp.toPx()), Size(m.size.width + 10.dp.toPx(), m.size.height + 4.dp.toPx()), CornerRadius(6.dp.toPx()))
                drawText(m, topLeft = tl)
            }
            revealed.forEach { pin(kmXY(it), it.label(s.fuel), Color(0xFF111111)) }
            pin(kmXY(HOME), "서울", Color(0xFF8C8C8C))

            // the car
            if (!done) {
                val o = px(now.p)
                rotate(now.headingDeg.toFloat(), o) {
                    val r = 11.dp.toPx()
                    val tri = Path().apply { moveTo(o.x, o.y - r * 1.2f); lineTo(o.x + r * 0.8f, o.y + r); lineTo(o.x, o.y + r * 0.5f); lineTo(o.x - r * 0.8f, o.y + r); close() }
                    drawPath(tri, Color.White); drawPath(tri, Color(0xFF111111), style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
                }
            }

            // credit required by OpenStreetMap
            val credit = measurer.measure("© OpenStreetMap contributors", small)
            val cl = Offset(w - credit.size.width - 12.dp.toPx(), h - credit.size.height - 8.dp.toPx())
            drawRoundRect(Color(0xCCFFFFFF), cl - Offset(5.dp.toPx(), 2.dp.toPx()), Size(credit.size.width + 10.dp.toPx(), credit.size.height + 4.dp.toPx()), CornerRadius(5.dp.toPx()))
            drawText(credit, topLeft = cl)
        }
        if (tiles.isEmpty()) Box(Modifier.align(Alignment.Center).padding(24.dp)) { Txt("지도를 불러오는 중…", 14f, color = Pal.OnStrong) }
    }
}
