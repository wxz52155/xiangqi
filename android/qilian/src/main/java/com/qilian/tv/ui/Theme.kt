package com.qilian.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.qilian.tv.R

/** 夜读棋室：墨绿底色 + 金色点缀 + 朱砂红。 */
object Palette {
    val Night = Color(0xFF0A1411)
    val Ink = Color(0xFF0F1D19)
    val InkRaised = Color(0xFF16291F)
    val InkCard = Color(0xFF1B3128)
    val InkLine = Color(0x33E8D7B0)
    val Gold = Color(0xFFE2B45A)
    val GoldSoft = Color(0xFFF2D59A)
    val GoldDeep = Color(0xFFB8883A)
    val Cinnabar = Color(0xFFCF4232)
    val CinnabarDeep = Color(0xFF9E2C22)
    val Jade = Color(0xFF5FB592)
    val Ivory = Color(0xFFF6EEDC)
    val IvoryMuted = Color(0xFFC9BFA8)
    val Muted = Color(0xFF8E9A90)

    val BoardLight = Color(0xFFEFD29A)
    val BoardDark = Color(0xFFD9AF6C)
    val BoardLine = Color(0xFF6E4E2C)
    val PieceFace = Color(0xFFFBF1DA)
    val PieceEdge = Color(0xFFE2C995)
    val PieceRed = Color(0xFFB72E24)
    val PieceBlack = Color(0xFF17241F)

    val background = Brush.linearGradient(listOf(Color(0xFF0B1714), Color(0xFF10231C), Color(0xFF0A1311)))
}

val SerifFamily = FontFamily(Font(R.font.qilian_serif, FontWeight.Bold))

object Type {
    val Display = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = 2.sp, color = Palette.Ivory)
    val Headline = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Palette.Ivory)
    val Title = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Palette.Ivory)
    val CardTitle = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Palette.Ivory)
    val Body = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, color = Palette.IvoryMuted)
    val BodyStrong = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = Palette.Ivory)
    val Label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = Palette.Muted)
    val Small = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, color = Palette.Muted)
}

@Composable
fun QilianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Gold,
            onPrimary = Palette.Night,
            secondary = Palette.Cinnabar,
            onSecondary = Palette.Ivory,
            background = Palette.Ink,
            onBackground = Palette.Ivory,
            surface = Palette.InkRaised,
            onSurface = Palette.Ivory,
            surfaceVariant = Palette.InkCard,
            onSurfaceVariant = Palette.IvoryMuted,
            border = Palette.Gold,
        ),
        content = content,
    )
}
