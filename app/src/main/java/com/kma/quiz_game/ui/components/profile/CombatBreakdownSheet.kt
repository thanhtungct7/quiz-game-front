package com.kma.quiz_game.ui.components.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kma.quiz_game.data.remote.dto.CombatBreakdownDto
import com.kma.quiz_game.data.remote.dto.CombatStatsDto
import com.kma.quiz_game.data.remote.dto.StatSourceDto
import com.kma.quiz_game.data.remote.dto.StatSourceKind
import com.kma.quiz_game.ui.screens.profile.CombatStat
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Neutral050
import com.kma.quiz_game.ui.theme.Neutral500
import com.kma.quiz_game.ui.theme.Neutral600
import com.kma.quiz_game.ui.theme.Neutral700
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500

/**
 * Where one of the three numbers on the card came from.
 *
 * The server sends every line as a delta and guarantees they sum to the total -- including the
 * case where the equipment ceiling clipped what the gear was labelled for, which it attributes to
 * the last item rather than leaving unexplained. So this sheet adds the column up and prints the
 * sum: a breakdown a player can check is the only kind worth opening.
 *
 * The tapped stat is the focused column, but the other three are still shown. A player asking
 * "why is my ATK 27" is usually one tap from asking what that same staff did to their defence,
 * and making them close the sheet and open another one would be the wrong answer to that.
 *
 * [fallbackTotal] is the figure already on the card. If the itemised request fails, the sheet
 * still says what the number *is*, which is better than an error where a number used to be.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombatBreakdownSheet(
    stat: CombatStat,
    breakdown: CombatBreakdownDto?,
    fallbackTotal: CombatStatsDto,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val total = breakdown?.total ?: fallbackTotal
    val sources = breakdown?.sources.orEmpty().filterNot { it.isEmpty }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stat.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stat.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = Neutral500,
            )

            Spacer(Modifier.height(16.dp))
            FocusedTotal(stat = stat, total = total)

            Spacer(Modifier.height(20.dp))
            when {
                isLoading && sources.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                sources.isEmpty() -> Text(
                    text = "Chưa bóc tách được nguồn điểm. Con số ở trên vẫn là con số trận đấu dùng.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Neutral500,
                )

                else -> SourceTable(stat = stat, sources = sources, total = total)
            }
        }
    }
}

@Composable
private fun FocusedTotal(stat: CombatStat, total: CombatStatsDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Neutral050)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${stat.valueOf(total)}",
            style = MaterialTheme.typography.displaySmall,
            color = stat.color,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(text = stat.label, style = MaterialTheme.typography.titleMedium, color = Neutral700)
            if (stat == CombatStat.ATK) {
                Text(
                    text = "Hệ số x${"%.2f".format(total.damagePermille / 1000f)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Neutral500,
                )
            }
        }
    }
}

/**
 * One row per source, four columns, focused column highlighted.
 *
 * The footer re-adds the focused column rather than echoing the total from above: if the two ever
 * disagree, the player sees it, and so does whoever is reading the bug report.
 */
@Composable
private fun SourceTable(stat: CombatStat, sources: List<StatSourceDto>, total: CombatStatsDto) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Text(
                text = "Nguồn",
                style = MaterialTheme.typography.labelMedium,
                color = Neutral500,
                modifier = Modifier.weight(1f),
            )
            ColumnHeading("HP", stat == CombatStat.HP)
            ColumnHeading("ATK", stat == CombatStat.ATK)
            ColumnHeading("DEF", stat == CombatStat.DEFENCE)
            ColumnHeading("MP", false)
        }
        HorizontalDivider()

        sources.forEach { source ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Neutral700,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = kindLabel(source.kind),
                        style = MaterialTheme.typography.labelSmall,
                        color = Neutral500,
                    )
                }
                DeltaCell(source.hp, stat == CombatStat.HP)
                DeltaCell(source.atk, stat == CombatStat.ATK)
                DeltaCell(source.defence, stat == CombatStat.DEFENCE)
                DeltaCell(source.mana, false)
            }
            HorizontalDivider(color = Neutral050)
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Tổng",
                style = MaterialTheme.typography.titleSmall,
                color = Neutral700,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${sources.sumOf { stat.valueOf(it) }}",
                style = MaterialTheme.typography.titleMedium,
                color = stat.color,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "Số liệu do máy chủ cộng sẵn — đây đúng là chỉ số trận đấu sẽ dùng.",
            style = MaterialTheme.typography.bodySmall,
            color = Neutral500,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ColumnHeading(text: String, focused: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (focused) Neutral700 else Neutral500,
        fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.End,
        modifier = Modifier.width(CELL_WIDTH),
    )
}

/** A zero is drawn as a dash: "this thing does nothing here" is easier to scan than a column of
 * noughts, and the row is only on screen at all because it moved something. */
@Composable
private fun DeltaCell(value: Int, focused: Boolean) {
    Text(
        text = if (value == 0) "—" else "+$value",
        style = MaterialTheme.typography.bodyMedium,
        color = when {
            value == 0 -> Neutral500
            focused -> Neutral700
            else -> Neutral600
        },
        fontWeight = if (focused && value != 0) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.End,
        modifier = Modifier.width(CELL_WIDTH),
    )
}

private fun kindLabel(kind: String): String = when (kind) {
    StatSourceKind.CLASS -> "Lớp nhân vật"
    StatSourceKind.EQUIPMENT -> "Trang bị"
    StatSourceKind.STREAK -> "Chuỗi ngày học"
    else -> kind
}

private val CombatStat.label: String
    get() = when (this) {
        CombatStat.HP -> "Máu tối đa"
        CombatStat.ATK -> "Sát thương mỗi câu đúng"
        CombatStat.DEFENCE -> "Giảm sát thương mỗi đòn"
    }

private val CombatStat.title: String
    get() = when (this) {
        CombatStat.HP -> "HP đến từ đâu"
        CombatStat.ATK -> "ATK đến từ đâu"
        CombatStat.DEFENCE -> "DEF đến từ đâu"
    }

private val CombatStat.explanation: String
    get() = when (this) {
        CombatStat.HP -> "Máu bạn mang vào trận, cộng từ lớp nhân vật, trang bị và chuỗi ngày học."
        CombatStat.ATK -> "Sát thương một câu trả lời đúng gây ra, sau khi nhân hệ số của lớp và trang bị."
        CombatStat.DEFENCE -> "Số sát thương trừ thẳng khỏi mỗi đòn đối phương đánh trúng."
    }

private val CombatStat.color: Color
    get() = when (this) {
        CombatStat.HP -> Green500
        CombatStat.ATK -> Rose500
        CombatStat.DEFENCE -> Sky500
    }

private fun CombatStat.valueOf(total: CombatStatsDto): Int = when (this) {
    CombatStat.HP -> total.hp
    CombatStat.ATK -> total.atk
    CombatStat.DEFENCE -> total.defence
}

private fun CombatStat.valueOf(source: StatSourceDto): Int = when (this) {
    CombatStat.HP -> source.hp
    CombatStat.ATK -> source.atk
    CombatStat.DEFENCE -> source.defence
}

private val CELL_WIDTH = 48.dp
