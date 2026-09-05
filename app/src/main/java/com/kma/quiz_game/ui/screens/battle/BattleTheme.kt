package com.kma.quiz_game.ui.screens.battle

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The battle screen's own palette: stone, parchment and firelight.
 *
 * Scoped to this screen on purpose, and not merged into [com.kma.quiz_game.ui.theme] -- the rest of
 * the app is a bright green language course, and a fight is the one place that reads better dark.
 * Everything here is local so restyling combat can never reach the lesson screens, the duo match or
 * the shared components under `ui/components/game`, which duo draws with the light theme.
 */
object BattleTheme {

    /** Surfaces, darkest first: the screen behind everything, the panel, a raised tile. */
    val Night = Color(0xFF12101A)
    val Stone = Color(0xFF1C1826)
    val StoneRaised = Color(0xFF272134)
    val StoneSunken = Color(0xFF0C0A12)

    /** Edges. [Edge] outlines a resting tile, [EdgeLit] one the player is meant to look at. */
    val Edge = Color(0xFF3D3450)
    val EdgeLit = Color(0xFF6B5A87)

    /** Text, brightest first. */
    val Parchment = Color(0xFFEDE3CC)
    val ParchmentDim = Color(0xFFA1957C)
    val ParchmentFaint = Color(0xFF6E6555)

    /** Meaning: gold is yours, blood is the monster's, ember is a warning. */
    val Gold = Color(0xFFE9B84C)
    val Blood = Color(0xFFC03A32)
    val Ember = Color(0xFFE8763A)
    val Mana = Color(0xFF43A9CC)
    val Venom = Color(0xFF74B84A)

    /** Health, by how much of it is left -- the same reading the shared bar gives, darker. */
    val HealthHigh = Color(0xFF5FA83C)
    val HealthMid = Color(0xFFD9962F)
    val HealthLow = Color(0xFFC03A32)

    /** Cut corners rather than round ones: a shield edge, not a chat bubble. */
    val TileShape: Shape = RoundedCornerShape(6.dp)
    val BarShape: Shape = RoundedCornerShape(3.dp)
    val PanelShape: Shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

    /** The panel the question and the skills stand on. */
    val panelBrush: Brush = Brush.verticalGradient(listOf(Stone, Night))

    /** A tile at rest, and the same tile when it is the one to press. */
    val tileBrush: Brush = Brush.verticalGradient(listOf(StoneRaised, Stone))
    val tileLitBrush: Brush = Brush.verticalGradient(listOf(Color(0xFF3A3050), Color(0xFF241E31)))

    /** Fills a bar with a light-to-dark run of one colour, so it reads as a lit surface. */
    fun barBrush(color: Color): Brush = Brush.verticalGradient(
        listOf(
            color.copy(alpha = 1f).lighten(0.28f),
            color,
            color.lighten(-0.22f),
        )
    )

    fun healthColor(fraction: Float): Color = when {
        fraction <= 0.25f -> HealthLow
        fraction <= 0.5f -> HealthMid
        else -> HealthHigh
    }
}

private fun Color.lighten(amount: Float): Color {
    val target = if (amount >= 0f) 1f else 0f
    val t = kotlin.math.abs(amount)
    return Color(
        red = red + (target - red) * t,
        green = green + (target - green) * t,
        blue = blue + (target - blue) * t,
        alpha = alpha,
    )
}
