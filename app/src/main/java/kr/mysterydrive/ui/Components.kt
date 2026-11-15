package kr.mysterydrive.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.mysterydrive.core.KIND_LABEL

// ---------------------------------------------------------------- images from assets
private object ImageCache {
    private val cache = LruCache<String, ImageBitmap>(48)
    fun get(context: android.content.Context, path: String): ImageBitmap? = cache[path]
        ?: runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull()?.also { cache.put(path, it) }
}

@Composable
fun AssetImage(path: String, modifier: Modifier = Modifier, description: String? = null) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, path) { value = withContext(Dispatchers.IO) { ImageCache.get(context, path) } }
    val shown = image
    if (shown != null) Image(shown, description, modifier, contentScale = ContentScale.Crop) else Box(modifier.background(Pal.Line))
}

// ---------------------------------------------------------------- marks and buttons
/** Category mark: shape and tone instead of colour. restaurants ● filled, cafes ○ grey, sights ◆ outlined. */
@Composable
fun Tag(kind: String) {
    val (glyph, bg, fg, border) = when (kind) {
        "맛집" -> arrayOf("●", Pal.Strong, Pal.OnStrong, Pal.Strong)
        "카페" -> arrayOf("○", Color(0xFF8F8F8F), Pal.OnStrong, Color(0xFF8F8F8F))
        "사진" -> arrayOf("◆", Color.Transparent, Pal.Ink, Pal.Faint)
        else -> arrayOf("●", Color.Transparent, Pal.Ink, Pal.Faint)
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bg as Color).border(BorderStroke(1.dp, border as Color), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Txt(glyph as String, 9f, color = fg as Color)
        Txt(KIND_LABEL[kind] ?: kind, 12f, FontWeight.Bold, fg)
    }
}

/** The one main action of a screen: bright, wide, bold. */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (enabled) Pal.Strong else Pal.Faint)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, 17f, FontWeight.Bold, Pal.OnStrong) }
}

/** A side action: outlined. [on] shows a chosen state. */
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, on: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (on) Color(0xFF3A3A3A) else Color.Transparent)
            .border(BorderStroke(1.dp, if (on) Pal.Mute else Color(0xFF6A6A6A)), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, 14f, FontWeight.Bold) }
}

/** Least important action: only underlined text. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Txt(text, 14f, color = Pal.Mute, underline = true, modifier = modifier.clickable(onClick = onClick).padding(vertical = 6.dp))
}

@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, soft: Boolean = false, onClick: () -> Unit) {
    val bg = when { selected -> Pal.Strong; soft -> Pal.Card; else -> Color.Transparent }
    val line = when { selected -> Pal.Strong; soft -> Pal.Line; else -> Color(0xFF444444) }
    Box(
        modifier.clip(RoundedCornerShape(50)).background(bg).border(BorderStroke(1.dp, line), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 15.dp, vertical = 8.dp),
    ) { Txt(text, 14f, if (selected) FontWeight.Bold else FontWeight.Normal, if (selected) Pal.OnStrong else Color(0xFFD0D0D0)) }
}

/** Options joined into one rounded bar; the chosen one is filled. */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, modifier: Modifier = Modifier, equalWidth: Boolean = false, onSelect: (String) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(12.dp)).border(BorderStroke(1.dp, Color(0xFF444444)), RoundedCornerShape(12.dp)).height(IntrinsicSize.Min)) {
        options.forEachIndexed { i, (value, label) ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF444444)))
            Box(
                (if (equalWidth) Modifier.weight(1f) else Modifier).background(if (value == selected) Pal.Strong else Color.Transparent).clickable { onSelect(value) }.padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(label, 14f, if (value == selected) FontWeight.Bold else FontWeight.Normal, if (value == selected) Pal.OnStrong else Color(0xFFD0D0D0)) }
        }
    }
}

// ---------------------------------------------------------------- sections
@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.Line))

/** A folded section: one line (title + short summary + a +/- mark) until tapped. */
@Composable
fun Accordion(title: String, summary: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Divider()
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(title, 15f, FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            Txt(summary, 12.5f, color = Pal.Mute, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.End)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(24.dp).border(BorderStroke(1.dp, Pal.Faint), CircleShape), contentAlignment = Alignment.Center) { Txt(if (open) "−" else "+", 15f) }
        }
        if (open) Column(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
fun MoreButton(label: String, open: Boolean, onToggle: () -> Unit) = GhostButton(if (open) "접기" else label, onClick = onToggle)

/** Small label above a group of choices. */
@Composable
fun GroupLabel(text: String) = Txt(text, 12f, FontWeight.Bold, Pal.Mute)

/** A key facts table: cells share hairlines, [perRow] cells per line. */
@Composable
fun Facts(cells: List<Pair<String, String>>, perRow: Int = 3) {
    val shown = cells.filter { it.first.isNotBlank() }
    if (shown.isEmpty()) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(BorderStroke(1.dp, Pal.Line), RoundedCornerShape(12.dp)).background(Pal.Line), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        shown.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                row.forEach { (value, label) ->
                    Column(Modifier.weight(1f).fillMaxHeight().background(Pal.Card).padding(horizontal = 14.dp, vertical = 11.dp)) {
                        Txt(value, 15.5f, FontWeight.Bold, lineHeight = 21f)
                        Txt(label, 11.5f, color = Pal.Mute, lineHeight = 16f)
                    }
                }
                repeat(perRow - row.size) { Box(Modifier.weight(1f).fillMaxHeight().background(Pal.Card)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Pills(content: @Composable () -> Unit) = FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }

@Composable
fun Card(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.Card).border(BorderStroke(1.dp, Pal.Line), RoundedCornerShape(14.dp)).padding(padding)) { content() }
}
