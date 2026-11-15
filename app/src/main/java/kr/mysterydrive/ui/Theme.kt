package kr.mysterydrive.ui

import android.content.Context
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/** Neutral greys only: the screen has no hue, so state is told apart by shape, weight and position. */
object Pal {
    val Bg = Color(0xFF0F0F0F)
    val Card = Color(0xFF1B1B1B)
    val Line = Color(0xFF303030)
    val Ink = Color(0xFFECECEC)
    val Mute = Color(0xFF8C8C8C)
    val Strong = Color(0xFFF2F2F2)
    val Faint = Color(0xFF5A5A5A)
    val OnStrong = Color(0xFF111111)
}

val LocalAppFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/**
 * NeoHyundai from assets/fonts when it is there (the files are kept out of git), the system font otherwise.
 * L=Light, R=Regular, B=Bold, EB=ExtraBold.
 */
fun loadAppFont(context: Context): FontFamily {
    val present = context.assets.list("fonts")?.toSet().orEmpty()
    fun font(file: String, weight: FontWeight) = if (file in present) Font("fonts/$file", context.assets, weight) else null
    val fonts = listOfNotNull(
        font("NeoHyundai-L.otf", FontWeight.Light), font("NeoHyundai-R.otf", FontWeight.Normal),
        font("NeoHyundai-B.otf", FontWeight.Bold), font("NeoHyundai-EB.otf", FontWeight.ExtraBold),
    )
    return if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
}

@Composable
fun MysteryTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val family = remember { loadAppFont(context) }
    val base = LocalDensity.current
    // The car screen is 2560 px wide at 160 dpi, so the whole interface is drawn 1.8 times larger (body text about 27 px, like the car's own text).
    val scaled = Density(base.density * 1.8f, base.fontScale)
    CompositionLocalProvider(LocalAppFont provides family, LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = darkColorScheme(background = Pal.Bg, surface = Pal.Card, onBackground = Pal.Ink, onSurface = Pal.Ink, primary = Pal.Strong),
            content = content,
        )
    }
}

/** The one text component: size in sp, weight, neutral colour, app font. */
@Composable
fun Txt(
    text: String,
    size: Float = 15f,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Pal.Ink,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    align: TextAlign? = null,
    underline: Boolean = false,
    lineHeight: Float? = null,
) {
    Text(
        text = text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = weight, fontFamily = LocalAppFont.current,
        maxLines = maxLines, overflow = overflow, textAlign = align, lineHeight = lineHeight?.sp ?: (size * 1.4f).sp,
        textDecoration = if (underline) TextDecoration.Underline else null,
    )
}
