package com.kma.quiz_game.ui.screens.profile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The submit rules mirror the backend's UserUpdate (username 1-50, bio at most 300). Getting
 * them wrong here costs a round trip and a 422 the user cannot act on.
 */
class ProfileUiStateTest {
    private fun state(
        username: String = "Player",
        bio: String = "Learning Vietnamese",
        draftUsername: String = "Player",
        draftBio: String = "Learning Vietnamese",
        isSaving: Boolean = false,
    ) = ProfileUiState(
        username = username,
        bio = bio,
        draftUsername = draftUsername,
        draftBio = draftBio,
        isSaving = isSaving,
    )

    @Test
    fun `an edited display name can be submitted`() {
        assertTrue(state(draftUsername = "Renamed").canSubmit)
    }

    @Test
    fun `an edited bio can be submitted`() {
        assertTrue(state(draftBio = "Something new").canSubmit)
    }

    @Test
    fun `an untouched form cannot be submitted`() {
        assertFalse(state().canSubmit)
    }

    @Test
    fun `surrounding whitespace alone is not a change`() {
        assertFalse(state(draftUsername = "  Player  ").hasChanges)
    }

    @Test
    fun `a display name longer than the backend allows cannot be submitted`() {
        assertFalse(state(draftUsername = "n".repeat(51)).canSubmit)
    }

    @Test
    fun `a display name of exactly the maximum length can be submitted`() {
        assertTrue(state(draftUsername = "n".repeat(50)).canSubmit)
    }

    @Test
    fun `a bio longer than the backend allows cannot be submitted`() {
        assertFalse(state(draftBio = "b".repeat(301)).canSubmit)
    }

    @Test
    fun `a bio of exactly the maximum length can be submitted`() {
        assertTrue(state(draftBio = "b".repeat(300)).canSubmit)
    }

    @Test
    fun `clearing the bio is a legitimate edit`() {
        assertTrue(state(draftBio = "").canSubmit)
    }

    @Test
    fun `a display name that is already set cannot be blanked`() {
        // The backend rejects a blank username outright, so the button has to stay disabled
        // rather than sending an edit that comes back as a 422.
        assertFalse(state(draftUsername = "   ").canSubmit)
    }

    @Test
    fun `an account that never had a display name may leave it empty`() {
        assertTrue(state(username = "", draftUsername = "", draftBio = "New bio").canSubmit)
    }

    @Test
    fun `a save already in flight cannot be submitted again`() {
        assertFalse(state(draftUsername = "Renamed", isSaving = true).canSubmit)
    }

    @Test
    fun `an account with no avatar reports none`() {
        assertFalse(ProfileUiState().hasAvatar)
        assertTrue(ProfileUiState(avatarUrl = "/api/v1/users/u/avatar?v=abc").hasAvatar)
    }

    @Test
    fun `a google picture is shown but cannot be removed`() {
        // Deleting it would 404: there is no uploaded file behind it to delete.
        val googleAvatar = ProfileUiState(avatarUrl = "https://lh3.googleusercontent.com/a/pic")

        assertTrue(googleAvatar.hasAvatar)
        assertFalse(googleAvatar.canRemoveAvatar)
    }

    @Test
    fun `an uploaded avatar can be removed unless an upload is in flight`() {
        val uploaded = ProfileUiState(
            avatarUrl = "/api/v1/users/u/avatar?v=abc",
            hasUploadedAvatar = true,
        )

        assertTrue(uploaded.canRemoveAvatar)
        assertFalse(uploaded.copy(isUploadingAvatar = true).canRemoveAvatar)
    }
}
