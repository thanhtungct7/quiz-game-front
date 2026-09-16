package com.kma.quiz_game.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kma.quiz_game.data.AvatarImage
import com.kma.quiz_game.data.local.ThemeMode
import com.kma.quiz_game.data.remote.dto.AchievementCategory
import com.kma.quiz_game.data.remote.dto.ItemDto
import com.kma.quiz_game.data.remote.dto.SelfProfileDto
import com.kma.quiz_game.ui.AppViewModelFactory
import com.kma.quiz_game.ui.components.DuoButton
import com.kma.quiz_game.ui.components.DuoButtonVariant
import com.kma.quiz_game.ui.components.game.rarityColor
import com.kma.quiz_game.ui.components.game.rarityLabel
import com.kma.quiz_game.ui.components.profile.AchievementBadge
import com.kma.quiz_game.ui.components.profile.AchievementProgressRow
import com.kma.quiz_game.ui.components.profile.CombatBreakdownSheet
import com.kma.quiz_game.ui.components.profile.LearningStatsBlock
import com.kma.quiz_game.ui.components.profile.PvpStatsBlock
import com.kma.quiz_game.ui.components.profile.RpgIdentityCard
import com.kma.quiz_game.ui.components.profile.SkillRadarChart
import com.kma.quiz_game.ui.components.profile.StatCellData
import com.kma.quiz_game.ui.components.profile.StatRow
import com.kma.quiz_game.ui.components.profile.categoryLabel
import com.kma.quiz_game.ui.components.profile.defaultRadarAxes
import com.kma.quiz_game.ui.rememberAppViewModelFactory
import com.kma.quiz_game.ui.theme.Green500
import com.kma.quiz_game.ui.theme.Orange400
import com.kma.quiz_game.ui.theme.Rose500
import com.kma.quiz_game.ui.theme.Sky500
import kotlinx.coroutines.launch

/**
 * The player's own profile: one RPG card, and three tabs of what is behind it.
 *
 * Built as a single [LazyColumn] rather than a card above a pager. Both the identity card and the
 * tab body scroll as one column, which is what the design asks for, and a pager would have to
 * nest a scroll container inside a scrolling parent to get it -- the arrangement that makes a
 * fling stall at the seam.
 *
 * That choice is also what makes switching tabs cheap. The card, the tab row and the header are
 * separate items with stable keys, so changing tab recycles only the items below the row: the
 * character keeps idling, its bars keep their animated values, and nothing above the tab row is
 * measured again.
 *
 * Data follows the same rule. The card arrives with the profile; the breakdown is fetched when a
 * bar is tapped and the shelf when the wardrobe is opened. A tab nobody visits costs nothing.
 */
@Composable
fun MyProfileScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    factory: AppViewModelFactory = rememberAppViewModelFactory(),
    viewModel: ProfileViewModel = viewModel(factory = factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Re-read on every visit: a skin put on in the Nhân vật tab is what colours the character
    // below, and this view model survives the tab swap that would otherwise have reloaded it.
    LaunchedEffect(Unit) { viewModel.load() }

    // The system photo picker: it hands back a single image without the app ever holding
    // READ_MEDIA_IMAGES, so there is no runtime permission to ask for.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            runCatching { AvatarImage.readScaledJpeg(context, uri) }
                .onSuccess { viewModel.uploadAvatar(it, AvatarImage.MIME_TYPE) }
                .onFailure { viewModel.onAvatarReadFailed() }
        }
    }
    val pickAvatar = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    if (uiState.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "card") {
            // Absent until the aggregated card arrives, and absent for good if it fails: identity
            // comes from a different endpoint, and a player whose card does not load must still
            // be able to see their name and edit it.
            uiState.card?.let { card ->
                RpgIdentityCard(
                    profile = card.asPublic(),
                    onOpenBreakdown = viewModel::openBreakdown,
                )
            }
        }

        // Above the tab row rather than inside the overview tab. Level, the bar towards the next
        // one and the CEFR band on offer are what `android.md` §3A says this screen is for, so
        // they must not sit one tab away behind a card about combat.
        item(key = "level") {
            uiState.card?.let { card ->
                SectionCard(title = "Cấp độ") {
                    LevelProgress(card)
                }
            }
        }

        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DuoButton(
                    text = "Chỉnh sửa",
                    onClick = viewModel::openEditor,
                    fullWidth = false,
                    modifier = Modifier.weight(1f),
                    leadingIcon = {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                    },
                )
                DuoButton(
                    text = "Đăng xuất",
                    onClick = onLogout,
                    variant = DuoButtonVariant.DangerOutline,
                    fullWidth = false,
                    modifier = Modifier.weight(1f),
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                    },
                )
            }
        }

        // The app has no settings screen, and this is the only screen about the player themselves.
        // It also earns its place at a demo: both palettes can be shown without leaving the app
        // for the system settings.
        item(key = "theme") {
            SectionCard(title = "Giao diện") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        ThemeChip(
                            label = mode.label,
                            selected = uiState.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                        )
                    }
                }
            }
        }

        uiState.errorMessage?.let { message ->
            item(key = "error") {
                Text(text = message, color = Rose500, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item(key = "tabs") {
            ProfileTabRow(selected = uiState.selectedTab, onSelect = viewModel::onTabSelected)
        }

        // Only these items change when a tab does. Everything above keeps its slot, which is why
        // the switch costs a relayout of the body rather than of the screen.
        when (uiState.selectedTab) {
            ProfileTab.OVERVIEW -> overviewTab(uiState)
            ProfileTab.STATISTICS -> statisticsTab(uiState)
            ProfileTab.WARDROBE -> wardrobeTab(uiState)
        }
    }

    uiState.breakdownStat?.let { stat ->
        CombatBreakdownSheet(
            stat = stat,
            breakdown = uiState.combat,
            fallbackTotal = uiState.card?.combat ?: com.kma.quiz_game.data.remote.dto.CombatStatsDto(),
            isLoading = uiState.isLoadingCombat,
            onDismiss = viewModel::closeBreakdown,
        )
    }

    if (uiState.isEditing) {
        EditProfileSheet(
            state = uiState,
            onUsernameChange = viewModel::onUsernameChange,
            onBioChange = viewModel::onBioChange,
            onPickAvatar = { pickAvatar() },
            onRemoveAvatar = viewModel::removeAvatar,
            onSubmit = viewModel::save,
            onDismiss = viewModel::closeEditor,
        )
    }
}

// --- tab 1: overview --------------------------------------------------------

/**
 * Level, the bar towards the next one, the ladder, and the three most recent badges.
 *
 * The answer to "how am I doing", in the order a player asks it.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.overviewTab(state: ProfileUiState) {
    val card = state.card ?: return

    item(key = "pvp") {
        SectionCard(title = null) {
            PvpStatsBlock(card.pvp)
        }
    }

    item(key = "featured") {
        SectionCard(
            title = "Thành tựu tiêu biểu",
            trailing = if (card.totalAchievementsUnlocked > 0) {
                "${card.totalAchievementsUnlocked} huy hiệu"
            } else {
                null
            },
        ) {
            val featured = state.featuredAchievements
            if (featured.isEmpty()) {
                EmptyNote("Chưa mở được huy hiệu nào. Học đều tay vài ngày là có cái đầu tiên.")
                return@SectionCard
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                featured.forEach { AchievementBadge(it) }
            }
        }
    }
}

@Composable
private fun LevelProgress(card: SelfProfileDto) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Lv.${card.level}",
                style = MaterialTheme.typography.headlineSmall,
                color = Sky500,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Còn ${card.expToNextLevel} EXP tới Lv.${card.level + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(card.levelFraction)
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Green500),
            )
        }

        Spacer(Modifier.height(14.dp))
        StatRow(
            StatCellData("Tổng EXP", "${card.totalExp}", Sky500),
            StatCellData("Vàng", "${card.gold}", Orange400),
            StatCellData("Chuỗi ngày", "${card.dayStreak}", Orange400),
        )

        card.nextCefr?.let { band ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Đạt bậc $band ở cấp ${card.nextCefrAtLevel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- tab 2: statistics ------------------------------------------------------

/** The study record as figures, then the same account as a shape. */
private fun androidx.compose.foundation.lazy.LazyListScope.statisticsTab(state: ProfileUiState) {
    val card = state.card ?: return

    item(key = "learning") {
        SectionCard(title = null) { LearningStatsBlock(card.learning) }
    }

    item(key = "figures") {
        SectionCard(title = "Số liệu chi tiết") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FigureRow("Bậc CEFR", card.cefr)
                FigureRow("TOEIC ước tính", "~${card.toeicEstimate}")
                FigureRow("Câu đã làm", "${card.learning.challengesAttempted}")
                FigureRow("Câu đã thuộc", "${card.learning.challengesMastered}")
                FigureRow("Lượt trả lời", "${card.learning.totalAttempts}")
                FigureRow("Độ chính xác", "${card.learning.accuracy}%")
                FigureRow("Chuỗi ngày hiện tại", "${card.dayStreak}")
                FigureRow("Kỷ lục chuỗi ngày", "${card.bestDayStreak}")
                FigureRow("Trận đã đấu", "${card.pvp.matchesPlayed}")
                FigureRow("Tỉ lệ thắng", if (card.pvp.hasPlayed) "${card.pvp.winRate}%" else "—")
            }
        }
    }

    item(key = "radar") {
        SectionCard(title = "Biểu đồ năng lực") {
            // Built once per card rather than on every recomposition: the axes are pure arithmetic
            // over four fields, and the chart animates on the values it is handed.
            val axes = remember(card) {
                defaultRadarAxes(card.learning, card.pvp, card.bestDayStreak)
            }
            SkillRadarChart(
                axes = axes,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            )
            Text(
                text = "Bốn trục là số liệu máy chủ thực sự đếm được. Tách theo bốn kỹ năng " +
                    "nghe/nói/đọc/viết cần dữ liệu gắn nhãn kỹ năng cho từng câu hỏi — hệ thống " +
                    "chưa lưu điều đó.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FigureRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

// --- tab 3: wardrobe --------------------------------------------------------

/**
 * What the player owns rather than what they have done.
 *
 * Skins and collectible cards come from the same inventory as equipment -- this is the half that
 * is worn rather than fought with, so the equipment screen and this tab never show the same item.
 * Titles have no source yet and say so; nothing is invented to fill the section.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.wardrobeTab(state: ProfileUiState) {
    item(key = "skins") {
        SectionCard(title = "Trang phục") {
            when {
                state.isLoadingInventory -> LoadingNote()
                state.skins.isEmpty() -> EmptyNote("Chưa có trang phục nào. Mua bằng Vàng ở tab Nhân vật.")
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.skins.forEach { OwnedItemRow(it) }
                }
            }
        }
    }

    item(key = "titles") {
        SectionCard(title = "Danh hiệu") {
            val title = state.card?.title
            if (title.isNullOrBlank()) {
                EmptyNote("Chưa sở hữu danh hiệu nào.")
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Orange400,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (state.collectibleCards.isNotEmpty()) {
        item(key = "cards") {
            SectionCard(title = "Thẻ sưu tầm") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.collectibleCards.forEach { OwnedItemRow(it) }
                }
            }
        }
    }

    item(key = "achievements-heading") {
        val shelf = state.achievements
        SectionCard(
            title = "Huy hiệu",
            trailing = shelf?.let { "${it.unlockedCount}/${it.total}" },
        ) {
            if (state.isLoadingAchievements && shelf == null) LoadingNote()
            else if (shelf == null) EmptyNote("Chưa tải được danh sách huy hiệu.")
            else EmptyNote("Huy hiệu mở khoá khi con số phía sau nó vượt ngưỡng.")
        }
    }

    // Flattened into the list rather than nested in a column inside one item: a shelf of twenty
    // badges is exactly the kind of thing lazy layout exists for.
    state.achievements?.let { shelf ->
        ACHIEVEMENT_CATEGORIES.forEach { category ->
            val items = shelf.byCategory(category)
            if (items.isEmpty()) return@forEach
            item(key = "ach-heading-$category") {
                Text(
                    text = categoryLabel(category),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(items = items, key = { "ach-${it.code}" }) { AchievementProgressRow(it) }
        }
    }
}

@Composable
private fun OwnedItemRow(item: ItemDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = rarityLabel(item.rarity),
                style = MaterialTheme.typography.labelSmall,
                color = rarityColor(item.rarity),
            )
        }
        if (item.quantity > 1) {
            Text(
                text = "x${item.quantity}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ACHIEVEMENT_CATEGORIES = listOf(
    AchievementCategory.PROGRESSION,
    AchievementCategory.LEARNING,
    AchievementCategory.PVP,
    AchievementCategory.PVE,
)

// --- shared chrome ----------------------------------------------------------

@Composable
private fun ProfileTabRow(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)),
    ) {
        ProfileTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (tab == selected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

private val ProfileTab.label: String
    get() = when (this) {
        ProfileTab.OVERVIEW -> "Tổng quan"
        ProfileTab.STATISTICS -> "Thống kê"
        ProfileTab.WARDROBE -> "Tủ đồ"
    }

/** The one container every block on this screen sits in, so a tab is a list of the same card. */
@Composable
private fun SectionCard(
    title: String?,
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
    ) {
        if (title != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                if (trailing != null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelLarge,
                        color = Sky500,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

/** One of the three palette choices. Same shape as the leaderboard's scope chips, on purpose. */
@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) Sky500 else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            .clickable(onClickLabel = "Chọn giao diện $label", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun EmptyNote(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LoadingNote() {
    Box(
        modifier = Modifier.fillMaxWidth().height(80.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}
