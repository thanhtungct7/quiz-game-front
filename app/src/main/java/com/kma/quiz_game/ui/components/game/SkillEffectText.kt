package com.kma.quiz_game.ui.components.game

import com.kma.quiz_game.data.remote.dto.SkillEffect

// What the two screens that name a skill call its effect: the loadout bar in a fight and
// the skill tree. The skill dock these once lived beside was dropped when both game modes
// moved to their own HUDs.

/** One glyph per effect, so a bar of three is readable without reading. */
fun effectSymbol(effect: String): String = when (effect) {
    SkillEffect.DOUBLE_DAMAGE -> "⚡"
    SkillEffect.DAMAGE_REDUCTION -> "🛡"
    SkillEffect.HEAL -> "💧"
    SkillEffect.TIME_PENALTY -> "⏳"
    SkillEffect.REMOVE_OPTIONS -> "👁"
    SkillEffect.MANA_BURN -> "🔥"
    SkillEffect.COMBO_KEEP -> "🔗"
    SkillEffect.EXECUTE -> "🗡"
    else -> "✨"
}

/**
 * What an effect does, in one clause.
 *
 * The magnitude is deliberately absent: it is read against the effect (percent, thousandths,
 * health, seconds) and the loadout payload does not carry it, so a number here would be guesswork.
 * The skill tree screen, which does have it, is where exact numbers belong.
 */
fun effectDescription(effect: String): String = when (effect) {
    SkillEffect.DOUBLE_DAMAGE -> "tăng mạnh sát thương đòn tới"
    SkillEffect.DAMAGE_REDUCTION -> "giảm sát thương phải chịu"
    SkillEffect.HEAL -> "hồi máu cho bạn"
    SkillEffect.TIME_PENALTY -> "rút ngắn thời gian của đối thủ"
    SkillEffect.REMOVE_OPTIONS -> "loại bớt đáp án sai (chỉ bạn thấy)"
    SkillEffect.MANA_BURN -> "đốt mana của đối thủ"
    SkillEffect.COMBO_KEEP -> "giữ chuỗi combo khi trả lời sai"
    SkillEffect.EXECUTE -> "kết liễu đối thủ đang kiệt máu"
    else -> "hiệu ứng đặc biệt"
}
