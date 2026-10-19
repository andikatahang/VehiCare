package com.vehicare.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceTypeVocabularyTest {

    @Test
    fun presetsAreExactlyTheSixLockedLabelsInOrder() {
        // D-23: Oil change · Tire rotation · Brake service · Inspection · Battery · Filters.
        assertEquals(
            listOf("Oil change", "Tire rotation", "Brake service", "Inspection", "Battery", "Filters"),
            ServiceTypePresets.ALL,
        )
    }

    @Test
    fun filterLearnedCollapsesCaseInsensitivePresetMatchesAndKeepsInputOrder() {
        val learned = listOf("oil change", "Wheel alignment", "OIL CHANGE", "Battery", "Detailing")

        assertEquals(
            listOf("Wheel alignment", "Detailing"),
            ServiceTypePresets.filterLearned(learned),
        )
    }

    @Test
    fun filterLearnedNeverRewritesStoredLabels() {
        // D-40: the stored value is stable — the filter drops matches, it never normalises case.
        val learned = listOf("oil Change", "Wheel alignment")

        assertEquals(listOf("Wheel alignment"), ServiceTypePresets.filterLearned(learned))
    }

    @Test
    fun filterLearnedPassesNonPresetLabelsThroughUnchanged() {
        val learned = listOf("Detailing", "AC service")

        assertEquals(learned, ServiceTypePresets.filterLearned(learned))
        assertEquals(emptyList(), ServiceTypePresets.filterLearned(emptyList()))
    }
}
