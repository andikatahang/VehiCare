package com.vehicare.notifications

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins every row of [NotificationPermissionPolicy]'s three truth tables — this object is the
 * whole D-54/D-61 gate and must not be inferred from one example.
 */
class NotificationPermissionPolicyTest {

    // --- isAskable ---------------------------------------------------------------------------

    @Test
    fun notDeterminedIsAskableWithOrWithoutTheSystemPromptBit() {
        assertTrue(
            NotificationPermissionPolicy.isAskable(
                NotificationPermission.NOT_DETERMINED,
                hasRequestedSystemPrompt = false,
            ),
        )
        assertTrue(
            NotificationPermissionPolicy.isAskable(
                NotificationPermission.NOT_DETERMINED,
                hasRequestedSystemPrompt = true,
            ),
        )
    }

    @Test
    fun grantedIsNeverAskableWithOrWithoutTheSystemPromptBit() {
        assertFalse(
            NotificationPermissionPolicy.isAskable(
                NotificationPermission.GRANTED,
                hasRequestedSystemPrompt = false,
            ),
        )
        assertFalse(
            NotificationPermissionPolicy.isAskable(
                NotificationPermission.GRANTED,
                hasRequestedSystemPrompt = true,
            ),
        )
    }

    @Test
    fun deniedWithoutTheSystemPromptBitIsAskable() {
        // The Android-before-the-first-ask case: Android has no NOT_DETERMINED, so a DENIED read
        // before the first ask must not read as a real denial.
        assertTrue(
            NotificationPermissionPolicy.isAskable(
                NotificationPermission.DENIED,
                hasRequestedSystemPrompt = false,
            ),
        )
    }

    @Test
    fun deniedWithTheSystemPromptBitIsNotAskable() {
        assertFalse(
            NotificationPermissionPolicy.isAskable(
                NotificationPermission.DENIED,
                hasRequestedSystemPrompt = true,
            ),
        )
    }

    // --- shouldShowSurface ---------------------------------------------------------------------

    @Test
    fun grantedNeverShowsTheSurfaceRegardlessOfRules() {
        assertFalse(
            NotificationPermissionPolicy.shouldShowSurface(
                NotificationPermission.GRANTED,
                hasEnabledRule = true,
            ),
        )
        assertFalse(
            NotificationPermissionPolicy.shouldShowSurface(
                NotificationPermission.GRANTED,
                hasEnabledRule = false,
            ),
        )
    }

    @Test
    fun notDeterminedShowsTheSurfaceOnlyWithAnEnabledRule() {
        assertTrue(
            NotificationPermissionPolicy.shouldShowSurface(
                NotificationPermission.NOT_DETERMINED,
                hasEnabledRule = true,
            ),
        )
        assertFalse(
            NotificationPermissionPolicy.shouldShowSurface(
                NotificationPermission.NOT_DETERMINED,
                hasEnabledRule = false,
            ),
        )
    }

    @Test
    fun deniedShowsTheSurfaceOnlyWithAnEnabledRule() {
        assertTrue(
            NotificationPermissionPolicy.shouldShowSurface(
                NotificationPermission.DENIED,
                hasEnabledRule = true,
            ),
        )
        assertFalse(
            NotificationPermissionPolicy.shouldShowSurface(
                NotificationPermission.DENIED,
                hasEnabledRule = false,
            ),
        )
    }

    // --- shouldAutoExplain ---------------------------------------------------------------------

    @Test
    fun notDeterminedAutoExplainsExactlyOnceRegardlessOfTheSystemPromptBit() {
        assertTrue(
            NotificationPermissionPolicy.shouldAutoExplain(
                NotificationPermission.NOT_DETERMINED,
                hasRequestedSystemPrompt = false,
                hasAutoExplained = false,
            ),
        )
        // Still askable (the process died mid-prompt), so still auto-explainable.
        assertTrue(
            NotificationPermissionPolicy.shouldAutoExplain(
                NotificationPermission.NOT_DETERMINED,
                hasRequestedSystemPrompt = true,
                hasAutoExplained = false,
            ),
        )
        // The amendment: once shown, never again — whatever the system-prompt bit says.
        assertFalse(
            NotificationPermissionPolicy.shouldAutoExplain(
                NotificationPermission.NOT_DETERMINED,
                hasRequestedSystemPrompt = false,
                hasAutoExplained = true,
            ),
        )
        assertFalse(
            NotificationPermissionPolicy.shouldAutoExplain(
                NotificationPermission.NOT_DETERMINED,
                hasRequestedSystemPrompt = true,
                hasAutoExplained = true,
            ),
        )
    }

    @Test
    fun deniedAutoExplainsOnlyBeforeTheFirstAskAndOnlyOnce() {
        assertTrue(
            NotificationPermissionPolicy.shouldAutoExplain(
                NotificationPermission.DENIED,
                hasRequestedSystemPrompt = false,
                hasAutoExplained = false,
            ),
        )
        assertFalse(
            NotificationPermissionPolicy.shouldAutoExplain(
                NotificationPermission.DENIED,
                hasRequestedSystemPrompt = true,
                hasAutoExplained = false,
            ),
        )
        assertFalse(
            NotificationPermissionPolicy.shouldAutoExplain(
                NotificationPermission.DENIED,
                hasRequestedSystemPrompt = false,
                hasAutoExplained = true,
            ),
        )
        assertFalse(
            NotificationPermissionPolicy.shouldAutoExplain(
                NotificationPermission.DENIED,
                hasRequestedSystemPrompt = true,
                hasAutoExplained = true,
            ),
        )
    }

    @Test
    fun grantedNeverAutoExplainsRegardlessOfEitherBit() {
        assertFalse(
            NotificationPermissionPolicy.shouldAutoExplain(
                NotificationPermission.GRANTED,
                hasRequestedSystemPrompt = false,
                hasAutoExplained = false,
            ),
        )
        assertFalse(
            NotificationPermissionPolicy.shouldAutoExplain(
                NotificationPermission.GRANTED,
                hasRequestedSystemPrompt = true,
                hasAutoExplained = true,
            ),
        )
    }
}
