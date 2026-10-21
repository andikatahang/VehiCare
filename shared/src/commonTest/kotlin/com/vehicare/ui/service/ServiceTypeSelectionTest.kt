package com.vehicare.ui.service

import com.vehicare.domain.ServiceTypePresets
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the pure chip transitions in `ServiceTypeSelection.kt`.
 *
 * The case that matters most is [confirmingANewLabelAddsBothTheSelectionAndTheChip]: it is the UAT
 * gap 1 invariant. The old ViewModel wrote only the selection, while the picker renders
 * `presets + learned`, so a brand-new label had no chip. Asserting **both** lists here (and again at
 * the ViewModel level in `iosTest`) is what a static trace could not prove.
 */
class ServiceTypeSelectionTest {

    @Test
    fun blankConfirmationIsANoOp() {
        val state = confirmCustomServiceType(
            label = "   ",
            selected = listOf("Battery"),
            learned = listOf("Wheel alignment"),
        )

        assertEquals(listOf("Battery"), state.selected)
        assertEquals(listOf("Wheel alignment"), state.learned)
    }

    @Test
    fun confirmingANewLabelAddsBothTheSelectionAndTheChip() {
        val state = confirmCustomServiceType(
            label = "  Wheel alignment  ",
            selected = emptyList(),
            learned = emptyList(),
        )

        // Gap 1: the selection is not enough — the label must also be renderable as a chip.
        assertEquals(listOf("Wheel alignment"), state.selected)
        assertEquals(listOf("Wheel alignment"), state.learned)

        // The picker's option set is presets + learned; prove the confirmed label is in it.
        val renderedOptions = ServiceTypePresets.ALL + state.learned
        assertEquals(1, renderedOptions.count { it == "Wheel alignment" })
    }

    @Test
    fun confirmingAPresetCaseVariantSelectsThePresetCasingAndAddsNoChip() {
        val state = confirmCustomServiceType(
            label = "oil change",
            selected = emptyList(),
            learned = emptyList(),
        )

        assertEquals(listOf("Oil change"), state.selected)
        assertEquals(emptyList(), state.learned)
    }

    @Test
    fun confirmingALearnedCaseVariantSelectsTheStoredCasingAndKeepsOneChip() {
        val state = confirmCustomServiceType(
            label = "WHEEL ALIGNMENT",
            selected = emptyList(),
            learned = listOf("Wheel alignment"),
        )

        // D-40: the stored label is never rewritten; the selection adopts its casing.
        assertEquals(listOf("Wheel alignment"), state.selected)
        assertEquals(listOf("Wheel alignment"), state.learned)
    }

    @Test
    fun confirmingTheSameLabelTwiceDoesNotDuplicateEitherList() {
        val first = confirmCustomServiceType(
            label = "Wheel alignment",
            selected = emptyList(),
            learned = emptyList(),
        )
        val second = confirmCustomServiceType(
            label = "wheel alignment",
            selected = first.selected,
            learned = first.learned,
        )

        assertEquals(listOf("Wheel alignment"), second.selected)
        assertEquals(listOf("Wheel alignment"), second.learned)
    }

    @Test
    fun aNewLabelIsPrependedSoItRendersRightAfterThePresets() {
        val state = confirmCustomServiceType(
            label = "Wheel alignment",
            selected = emptyList(),
            learned = listOf("Older label"),
        )

        assertEquals(listOf("Wheel alignment", "Older label"), state.learned)
    }

    @Test
    fun selectionOrderIsTapOrder() {
        var state = confirmCustomServiceType("Brake service", emptyList(), emptyList())
        state = confirmCustomServiceType("Oil change", state.selected, state.learned)
        state = confirmCustomServiceType("Wheel alignment", state.selected, state.learned)

        assertEquals(
            listOf("Brake service", "Oil change", "Wheel alignment"),
            state.selected,
        )
    }

    @Test
    fun togglingAppendsThenRemovesCaseInsensitively() {
        val afterFirstTap = toggleServiceType("Battery", emptyList())
        assertEquals(listOf("Battery"), afterFirstTap)

        val afterSecondTap = toggleServiceType("battery", afterFirstTap)
        assertEquals(emptyList(), afterSecondTap)
    }

    @Test
    fun togglingRemovesOnlyTheMatchingLabelAndKeepsTheOthersInOrder() {
        val result = toggleServiceType(
            label = "OIL CHANGE",
            selected = listOf("Battery", "Oil change", "Filters"),
        )

        assertEquals(listOf("Battery", "Filters"), result)
    }

    @Test
    fun togglingTheLastChipLeavesAnEmptySelection() {
        val result = toggleServiceType("Filters", listOf("Filters"))

        assertEquals(emptyList(), result)
    }
}
