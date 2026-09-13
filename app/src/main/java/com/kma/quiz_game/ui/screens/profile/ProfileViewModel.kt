package com.kma.quiz_game.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kma.quiz_game.data.remote.dto.AchievementListDto
import com.kma.quiz_game.data.remote.dto.CombatBreakdownDto
import com.kma.quiz_game.data.remote.dto.InventoryDto
import com.kma.quiz_game.data.remote.dto.ItemDto
import com.kma.quiz_game.data.remote.dto.ItemKind
import com.kma.quiz_game.data.remote.dto.SelfProfileDto
import com.kma.quiz_game.data.remote.dto.UserRead
import com.kma.quiz_game.data.remote.toUserMessage
import com.kma.quiz_game.data.repository.GameRepository
import com.kma.quiz_game.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_USERNAME_LENGTH = 50
const val MAX_BIO_LENGTH = 300

/** The three faces of the profile. Which one is showing is state, not navigation: a tab is not a
 * place a player can be sent back to, and putting it on the back stack would make the system
 * back gesture walk the tabs instead of leaving the screen. */
enum class ProfileTab { OVERVIEW, STATISTICS, WARDROBE }

/**
 * The bar a player tapped to ask where a number came from.
 *
 * Mana is resolved and carried alongside the other three, but it is not one of the three bars --
 * the card shows what a fight is won or lost on, and mana is a budget rather than a measure of
 * the build. It still appears as a column inside the breakdown.
 */
enum class CombatStat { HP, ATK, DEFENCE }

data class ProfileUiState(
    val userId: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val hasUploadedAvatar: Boolean = false,
    val isLoading: Boolean = true,
    val isUploadingAvatar: Boolean = false,
    val errorMessage: String? = null,
    /** What is stored, kept apart from the draft so the edit screen knows what changed. */
    val username: String = "",
    val bio: String = "",
    val draftUsername: String = "",
    val draftBio: String = "",
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /**
     * The aggregated card: level, CEFR band, PvP record and study totals in one payload.
     *
     * Null while it is still in flight, or when it failed. It is deliberately not required for
     * the screen to render -- identity loads from a different endpoint, and a player whose card
     * fails to arrive should still see their own name and be able to edit it.
     */
    val card: SelfProfileDto? = null,

    // --- the tabs ---
    val selectedTab: ProfileTab = ProfileTab.OVERVIEW,

    /**
     * The itemised build, fetched the first time a bar is tapped.
     *
     * Kept after the modal closes so reopening it is instant; it cannot go stale while the screen
     * is open, because nothing on this screen changes a build.
     */
    val combat: CombatBreakdownDto? = null,
    val isLoadingCombat: Boolean = false,
    /** Which bar the open modal is about. Null means the modal is closed. */
    val breakdownStat: CombatStat? = null,

    /** The whole shelf, fetched when the wardrobe tab is first opened rather than with the card:
     * it is the heaviest payload here and most sessions never open it. */
    val achievements: AchievementListDto? = null,
    val isLoadingAchievements: Boolean = false,

    /** Skins and cards live in the same inventory as equipment; the wardrobe shows the half that
     * is worn rather than fought with. */
    val inventory: InventoryDto? = null,
    val isLoadingInventory: Boolean = false,

    /** The edit sheet. A modal rather than a screen, so a rename does not cost a navigation. */
    val isEditing: Boolean = false,
) {
    val hasAvatar: Boolean get() = avatarUrl != null

    /** Only an uploaded avatar can be removed -- see [ProfileUiState.hasUploadedAvatar]. */
    val canRemoveAvatar: Boolean get() = hasUploadedAvatar && !isUploadingAvatar

    /**
     * Mirrors the backend's own limits: username at most 50, bio at most 300. Blanking a
     * display name that is already set is rejected here because the backend rejects it too --
     * catching it in the UI is the difference between a message and a silent no-op.
     */
    val isDraftValid: Boolean
        get() {
            val name = draftUsername.trim()
            val nameIsValid = name.length <= MAX_USERNAME_LENGTH &&
                (name.isNotEmpty() || username.isEmpty())
            return nameIsValid && draftBio.trim().length <= MAX_BIO_LENGTH
        }

    val hasChanges: Boolean
        get() = draftUsername.trim() != username || draftBio.trim() != bio

    val canSubmit: Boolean get() = !isSaving && isDraftValid && hasChanges

    /** Worn, not fought with: the wardrobe draws these and the equipment screen draws the rest. */
    val skins: List<ItemDto>
        get() = inventory?.items.orEmpty().filter { it.kind == ItemKind.SKIN }

    /** Collectible cards, shown beside the skins -- they are the other thing a player owns that
     * does nothing in a fight. */
    val collectibleCards: List<ItemDto>
        get() = inventory?.items.orEmpty().filter { it.kind == ItemKind.CARD }

    /**
     * The badges the overview tab shows.
     *
     * From the card rather than from [achievements], so the three are there on first paint
     * without waiting for the shelf -- the shelf is only fetched if the wardrobe tab is opened.
     */
    val featuredAchievements get() = card?.featuredAchievements.orEmpty()
}

/**
 * Backed by [ProfileRepository]'s cached profile rather than by its own copy: the profile and
 * the edit screens each get their own instance of this ViewModel, and collecting the shared
 * flow is what keeps them from drifting apart after a save.
 */
class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val gameRepository: GameRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.profile.filterNotNull().collect { user ->
                _uiState.update { it.applyProfile(user) }
            }
        }
        // A second collector, not a second fetch: the repository refreshes the card after every
        // rename and avatar change, and this is what carries that through to the screen.
        viewModelScope.launch {
            profileRepository.selfProfile.collect { card ->
                _uiState.update { it.copy(card = card) }
            }
        }
    }

    /**
     * Called by the screen on entering composition rather than from `init`: this view model
     * outlives its tab, and what the card draws can be changed from another one -- putting on a
     * skin in Nhân vật is what lights the character's plinth here.
     */
    fun load() {
        // Only a first, uncached load is worth a spinner; a revalidation should leave whatever
        // is already on screen alone.
        _uiState.update {
            it.copy(isLoading = profileRepository.profile.value == null, errorMessage = null)
        }
        viewModelScope.launch {
            profileRepository.refresh().onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
            }
        }
        // Failing this is not worth an error message: the identity above is what the screen
        // needs to be usable, and the card is an enrichment that simply stays absent.
        viewModelScope.launch { profileRepository.refreshSelfProfile() }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(draftUsername = value, errorMessage = null, isSaved = false) }
    }

    fun onBioChange(value: String) {
        _uiState.update { it.copy(draftBio = value, errorMessage = null, isSaved = false) }
    }

    /** Throws away an abandoned edit so reopening the screen starts from what is stored. */
    fun discardDraft() {
        _uiState.update {
            it.copy(
                draftUsername = it.username,
                draftBio = it.bio,
                errorMessage = null,
                isSaved = false,
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null, isSaved = false) }
        viewModelScope.launch {
            val username = state.draftUsername.trim()
            profileRepository.updateProfile(
                // Only send what changed: an omitted field means "leave it alone" to the
                // backend, which is one less way to clobber the other one.
                username = username.takeIf { it != state.username && it.isNotEmpty() },
                // "" clears the bio; null would be dropped from the body and change nothing.
                bio = state.draftBio.trim().takeIf { it != state.bio },
            )
                .onSuccess { _uiState.update { it.copy(isSaved = true) } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = error.toUserMessage())
                    }
                }
        }
    }

    fun uploadAvatar(content: ByteArray, mimeType: String) {
        _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null) }
        viewModelScope.launch {
            profileRepository.uploadAvatar(content, mimeType).onFailure { error ->
                _uiState.update {
                    it.copy(isUploadingAvatar = false, errorMessage = error.toUserMessage())
                }
            }
        }
    }

    fun removeAvatar() {
        _uiState.update { it.copy(isUploadingAvatar = true, errorMessage = null) }
        viewModelScope.launch {
            profileRepository.removeAvatar().onFailure { error ->
                _uiState.update {
                    it.copy(isUploadingAvatar = false, errorMessage = error.toUserMessage())
                }
            }
        }
    }

    /** Surfaces a failure that happened before any request -- an unreadable picked image. */
    fun onAvatarReadFailed() {
        _uiState.update {
            it.copy(
                isUploadingAvatar = false,
                errorMessage = "Không đọc được ảnh đã chọn. Hãy thử ảnh khác.",
            )
        }
    }

    /**
     * Switch tabs, and fetch what that tab needs if it has not been fetched yet.
     *
     * Loading is per-tab and once-only. Fetching all three up front would make the first paint
     * wait on two payloads most sessions never look at; refetching on every switch would make the
     * tabs feel slower the longer the screen stays open.
     */
    fun onTabSelected(tab: ProfileTab) {
        if (_uiState.value.selectedTab == tab) return
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == ProfileTab.WARDROBE) loadWardrobe()
    }

    /** Opens the breakdown for one bar, fetching the lines the first time. */
    fun openBreakdown(stat: CombatStat) {
        _uiState.update { it.copy(breakdownStat = stat) }
        loadCombatBreakdown()
    }

    fun closeBreakdown() {
        _uiState.update { it.copy(breakdownStat = null) }
    }

    /** Opens the edit sheet on what is currently stored, discarding any abandoned draft. */
    fun openEditor() {
        _uiState.update {
            it.copy(
                isEditing = true,
                draftUsername = it.username,
                draftBio = it.bio,
                errorMessage = null,
                isSaved = false,
            )
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(isEditing = false) }
        discardDraft()
    }

    private fun loadCombatBreakdown() {
        val state = _uiState.value
        if (state.combat != null || state.isLoadingCombat) return
        _uiState.update { it.copy(isLoadingCombat = true) }
        viewModelScope.launch {
            profileRepository.combatBreakdown()
                .onSuccess { breakdown ->
                    _uiState.update { it.copy(combat = breakdown, isLoadingCombat = false) }
                }
                // The modal falls back to the totals the card already carries, so a failed
                // breakdown costs the itemisation rather than the whole sheet.
                .onFailure { _uiState.update { it.copy(isLoadingCombat = false) } }
        }
    }

    /**
     * The two payloads behind the wardrobe tab, fetched together the first time it is opened.
     *
     * Neither failure is worth an error banner: each section says it is empty, which is what an
     * account with nothing in it would show anyway.
     */
    private fun loadWardrobe() {
        val state = _uiState.value
        if (state.achievements == null && !state.isLoadingAchievements) {
            _uiState.update { it.copy(isLoadingAchievements = true) }
            viewModelScope.launch {
                profileRepository.achievements()
                    .onSuccess { list ->
                        _uiState.update {
                            it.copy(achievements = list, isLoadingAchievements = false)
                        }
                    }
                    .onFailure { _uiState.update { it.copy(isLoadingAchievements = false) } }
            }
        }
        if (state.inventory == null && !state.isLoadingInventory) {
            _uiState.update { it.copy(isLoadingInventory = true) }
            viewModelScope.launch {
                gameRepository.inventory()
                    .onSuccess { held ->
                        _uiState.update { it.copy(inventory = held, isLoadingInventory = false) }
                    }
                    .onFailure { _uiState.update { it.copy(isLoadingInventory = false) } }
            }
        }
    }

    private fun ProfileUiState.applyProfile(user: UserRead) = copy(
        userId = user.id,
        email = user.email,
        avatarUrl = user.avatarUrl,
        hasUploadedAvatar = user.hasUploadedAvatar,
        username = user.username.orEmpty(),
        bio = user.bio.orEmpty(),
        draftUsername = user.username.orEmpty(),
        draftBio = user.bio.orEmpty(),
        isLoading = false,
        isUploadingAvatar = false,
        isSaving = false,
        errorMessage = null,
    )
}
