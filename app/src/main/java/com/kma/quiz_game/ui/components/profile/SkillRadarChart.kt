package com.kma.quiz_game.ui.components.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kma.quiz_game.data.remote.dto.LearningStatsDto
import com.kma.quiz_game.data.remote.dto.PvpStatsDto
import com.kma.quiz_game.ui.theme.Neutral100
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Quiz_gameTheme
import com.kma.quiz_game.ui.theme.Sky500
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One axis of the radar: what it is called, and how far along it the player is.
 *
 * [raw] is the number itself, kept beside the normalised [fraction] so the label can say "58%"
 * rather than leaving the player to guess what two thirds of an unnamed axis means.
 */
data class RadarAxis(val label: String, val raw: String, val fraction: Float)

/**
 * Four measures of a player, on one shape.
 *
 * These are **not** the four language skills. The app does not record listening, reading, speaking
 * or writing separately -- `user_challenge_progress` keeps one best-ever outcome per question and
 * nothing about which skill the question exercised -- so an L/R/S/W radar here would be four made-up
 * numbers drawn with the authority of a measurement. These four axes are things the server really
 * counts. When the content module starts tagging challenges by skill, this composable takes the
 * axes it is given and nothing else has to change.
 */
@Composable
fun SkillRadarChart(
    axes: List<RadarAxis>,
    modifier: Modifier = Modifier,
) {
    if (axes.size < 3) return
    val measurer = rememberTextMeasurer()
    // One animation for the whole shape rather than one per axis: the polygon has to stay a
    // polygon while it grows, and four independently-timed vertices would ripple.
    val grown by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700),
        label = "radar",
    )

    Canvas(modifier = modifier) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        // Leave room for the labels drawn outside the outermost ring.
        val radius = (size.minDimension / 2f) - LABEL_ROOM.toPx()

        drawWeb(centre, radius, axes.size)
        drawValues(centre, radius, axes, grown)
        drawLabels(centre, radius, axes, measurer)
    }
}

/** The grid: four rings and a spoke per axis. */
private fun DrawScope.drawWeb(centre: Offset, radius: Float, axisCount: Int) {
    repeat(RING_COUNT) { ring ->
        val ringRadius = radius * (ring + 1) / RING_COUNT
        val path = Path()
        for (index in 0 until axisCount) {
            val point = vertex(centre, ringRadius, index, axisCount)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawPath(path, color = Neutral100, style = Stroke(width = 1f))
    }
    for (index in 0 until axisCount) {
        drawLine(
            color = Neutral100,
            start = centre,
            end = vertex(centre, radius, index, axisCount),
            strokeWidth = 1f,
        )
    }
}

private fun DrawScope.drawValues(
    centre: Offset,
    radius: Float,
    axes: List<RadarAxis>,
    grown: Float,
) {
    val path = Path()
    axes.forEachIndexed { index, axis ->
        val reach = radius * axis.fraction.coerceIn(0f, 1f) * grown
        val point = vertex(centre, reach, index, axes.size)
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()

    drawPath(path, color = Sky500.copy(alpha = 0.22f))
    drawPath(path, color = Sky500, style = Stroke(width = 2.5f))
    axes.forEachIndexed { index, axis ->
        val reach = radius * axis.fraction.coerceIn(0f, 1f) * grown
        drawCircle(color = Sky500, radius = 4f, center = vertex(centre, reach, index, axes.size))
    }
}

/**
 * The axis names, nudged off the rim so they sit beside their spoke rather than on it.
 *
 * Each label is measured and then offset by half its own box, which is what keeps the left-hand
 * and right-hand labels from hanging off opposite edges of the canvas.
 */
private fun DrawScope.drawLabels(
    centre: Offset,
    radius: Float,
    axes: List<RadarAxis>,
    measurer: TextMeasurer,
) {
    axes.forEachIndexed { index, axis ->
        val anchor = vertex(centre, radius + LABEL_GAP.toPx(), index, axes.size)
        val name = measurer.measure(
            text = axis.label,
            style = TextStyle(fontSize = 11.sp, color = Neutral500, fontWeight = FontWeight.Medium),
        )
        val value = measurer.measure(
            text = axis.raw,
            style = TextStyle(fontSize = 12.sp, color = Sky500, fontWeight = FontWeight.Bold),
        )
        val blockHeight = name.size.height + value.size.height
        drawText(
            textLayoutResult = name,
            topLeft = Offset(anchor.x - name.size.width / 2f, anchor.y - blockHeight / 2f),
        )
        drawText(
            textLayoutResult = value,
            topLeft = Offset(
                x = anchor.x - value.size.width / 2f,
                y = anchor.y - blockHeight / 2f + name.size.height,
            ),
        )
    }
}

/** Vertex [index] of a regular polygon, first one straight up. */
private fun vertex(centre: Offset, radius: Float, index: Int, count: Int): Offset {
    val angle = -PI / 2 + 2.0 * PI * index / count
    return Offset(
        x = centre.x + radius * cos(angle).toFloat(),
        y = centre.y + radius * sin(angle).toFloat(),
    )
}

/**
 * The four axes, built from what the server actually counts.
 *
 * The denominators are the point where this stops being arithmetic and starts being a judgement,
 * so they are named: a full axis means "as far as this app expects anyone to get", not "the best
 * anyone has ever done". Accuracy is the only one already a percentage.
 */
fun defaultRadarAxes(learning: LearningStatsDto, pvp: PvpStatsDto, bestDayStreak: Int): List<RadarAxis> =
    listOf(
        RadarAxis(
            label = "Chính xác",
            raw = "${learning.accuracy.toInt()}%",
            fraction = (learning.accuracy / 100.0).toFloat(),
        ),
        RadarAxis(
            label = "Vốn câu",
            raw = "${learning.challengesMastered}",
            fraction = learning.challengesMastered.toFloat() / MASTERED_FULL,
        ),
        RadarAxis(
            label = "Đấu trường",
            raw = "${pvp.rating}",
            fraction = (pvp.rating - RATING_FLOOR).toFloat() / (RATING_FULL - RATING_FLOOR),
        ),
        RadarAxis(
            label = "Chuyên cần",
            raw = "$bestDayStreak ngày",
            fraction = bestDayStreak.toFloat() / STREAK_FULL,
        ),
    ).map { it.copy(fraction = it.fraction.coerceIn(0f, 1f)) }

/** Roughly a full course's worth of questions. */
private const val MASTERED_FULL = 500f

/** The ladder's own floor and the rating the top tier opens at, from `services/game/season.py`. */
private const val RATING_FLOOR = 1000f
private const val RATING_FULL = 2000f

/** A month of unbroken study, which is the longest streak the achievement catalog asks for. */
private const val STREAK_FULL = 30f

private const val RING_COUNT = 4
private val LABEL_ROOM = 34.dp
private val LABEL_GAP = 18.dp

@Preview(showBackground = true, widthDp = 320, heightDp = 320)
@Composable
private fun SkillRadarChartPreview() {
    Quiz_gameTheme {
        Box(Modifier.fillMaxWidth().height(300.dp).padding(12.dp)) {
            SkillRadarChart(
                axes = defaultRadarAxes(
                    learning = LearningStatsDto(420, 355, 610, 58.2),
                    pvp = PvpStatsDto(rating = 1420, tier = "GOLD", matchesPlayed = 30, wins = 19, losses = 9, draws = 2, winRate = 63.3),
                    bestDayStreak = 40,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
