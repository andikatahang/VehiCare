package com.vehicare.ui.service

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The UAT gap 1 state transition, proven on both platforms.
 *
 * A static trace of the ViewModel looked correct while the shipped behaviour was broken: the picker
 * renders `presets + learned`, so a confirmed custom label that only reached the selection had no
 * chip to render. These tests assert the one invariant that would have caught it — after confirming a
 * label it must be in **both** `values.serviceTypes` and `learnedTypes`, i.e. in the option set the
 * picker actually renders.
 *
 * The ViewModel-level call site is covered separately by
 * `iosTest/.../ServiceEntryEditViewModelCustomTypeTest.kt`, because `viewModelScope` needs a
 * `Dispatchers.setMain` test dispatcher.
 */
class ServiceEntryEditViewModelStateTest {

    private fun ready(
        learnedTypes: List<String> = emptyList(),
        serviceTypes: List<String> = emptyList(),
    ): ServiceEntryEditUiState.Ready = ServiceEntryEditUiState.Ready(
        values = ServiceEntryFormValues(
            dateEpochMs = 1L,
            mileage = "1000",
            serviceTypes = serviceTypes,
        ),
        learnedTypes = learnedTypes,
    )

    @Test
    fun confirmedCustomLabelEntersBothTheSelectionAndTheRenderedChipList() {
        val next = ready().withConfirmedCustomType("Wheel alignment")

        assertEquals(listOf("Wheel alignment"), next.values.serviceTypes)
        assertEquals(listOf("Wheel alignment"), next.learnedTypes)

        // What the picker renders: presets + learned. The selected label must have a chip.
        val rendered = com.vehicare.domain.ServiceTypePresets.ALL + next.learnedTypes
        assertEquals(1, rendered.count { it == "Wheel alignment" })
    }

    @Test
    fun confirmedPresetCaseVariantSelectsThePresetAndAddsNoLearnedChip() {
        val next = ready().withConfirmedCustomType("oil change")

        assertEquals(listOf("Oil change"), next.values.serviceTypes)
        assertEquals(emptyList(), next.learnedTypes)
    }

    @Test
    fun confirmingTheSameLabelTwiceKeepsOneSelectionAndOneChip() {
        val once = ready().withConfirmedCustomType("Wheel alignment")
        val twice = once.withConfirmedCustomType("wheel alignment")

        assertEquals(listOf("Wheel alignment"), twice.values.serviceTypes)
        assertEquals(listOf("Wheel alignment"), twice.learnedTypes)
    }

    @Test
    fun togglingChangesTheSelectionOnly() {
        val added = ready(learnedTypes = listOf("Wheel alignment")).withToggledType("Battery")
        assertEquals(listOf("Battery"), added.values.serviceTypes)
        // The learned chip list is a rendering concern; a toggle must never mutate it.
        assertEquals(listOf("Wheel alignment"), added.learnedTypes)

        val removed = added.withToggledType("battery")
        assertEquals(emptyList(), removed.values.serviceTypes)
        assertEquals(listOf("Wheel alignment"), removed.learnedTypes)
    }

    @Test
    fun togglingSeveralChipsKeepsTapOrder() {
        val next = ready()
            .withToggledType("Brake service")
            .withToggledType("Oil change")
            .withToggledType("Inspection")

        assertEquals(
            listOf("Brake service", "Oil change", "Inspection"),
            next.values.serviceTypes,
        )
    }
}
