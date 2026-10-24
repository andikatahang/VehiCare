package com.vehicare.ui.service

import com.vehicare.domain.ServiceTypePresets

/**
 * The outcome of confirming a custom service-type label: the next selection **and** the next
 * learned-chip list, produced together so the two can never drift apart.
 *
 * @param selected the chips that are currently selected, in tap order
 * @param learned  the learned custom labels the picker renders after the presets, newest first
 */
internal data class ServiceTypeSelectionState(
    val selected: List<String>,
    val learned: List<String>,
)

/**
 * The single confirm transition behind "Add custom…".
 *
 * Closes UAT gap 1: the old ViewModel wrote only the selection field (one string), while the
 * picker renders `presets + learned`, so a brand-new label had no chip to render and the user's
 * action looked like it did nothing. This transition returns the next selection **and** the next
 * learned list, and [ServiceEntryEditUiState.Ready.withConfirmedCustomType] writes both in one
 * `copy`. The picker's union of selected labels into its options is a second, defensive guard —
 * not the primary fix.
 *
 * Label handling preserves D-40: a case-insensitive match resolves to the existing preset (its own
 * casing) or the already-stored learned label (its stored casing); a stored label is never
 * rewritten. Only a genuinely new label is used verbatim (trimmed).
 *
 * @param label    the trimmed label the dialog handed over
 * @param selected the current selection, in tap order
 * @param learned  the current learned labels, newest first
 * @param presets  the preset vocabulary the picker renders first
 */
internal fun confirmCustomServiceType(
    label: String,
    selected: List<String>,
    learned: List<String>,
    presets: List<String> = ServiceTypePresets.ALL,
): ServiceTypeSelectionState {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return ServiceTypeSelectionState(selected, learned)

    val canonical = presets.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        ?: learned.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        ?: trimmed

    // Selection order is tap order, and one label may appear at most once (D-40 collapse).
    val nextSelected = if (selected.any { it.equals(canonical, ignoreCase = true) }) {
        selected
    } else {
        selected + canonical
    }

    // A preset or an already-learned label already has a chip; only a new label becomes one, and
    // it is prepended so it renders immediately after the presets instead of at the tail.
    val alreadyRenderable = presets.any { it.equals(canonical, ignoreCase = true) } ||
        learned.any { it.equals(canonical, ignoreCase = true) }
    val nextLearned = if (alreadyRenderable) learned else listOf(canonical) + learned

    return ServiceTypeSelectionState(selected = nextSelected, learned = nextLearned)
}

/**
 * Which gesture model a chip group follows.
 *
 * [Multiple] is the service-entry form's contract (any number selected, a tap toggles). [Single] is
 * the reminder editor's (C-32): exactly one type per rule, and a tap on the selected chip is a
 * **no-op** rather than a deselect, so the submit gate can never become unreachable while the form
 * shows no error.
 */
enum class SelectionMode {
    /** Toggle semantics: a tap adds an unselected label, removes a selected one. */
    Multiple,

    /** One-of semantics: a tap moves the selection, and a tap on the selected label does nothing. */
    Single,
}

/**
 * The single chip-tap transition for both modes (C-32).
 *
 * In [SelectionMode.Multiple] this **delegates** to [toggleServiceType] unchanged, so the
 * service-entry form's behaviour cannot drift from its own tests. In [SelectionMode.Single] the
 * result is always a one-element list: a different label replaces the selection, and the label that
 * is already selected (compared case-insensitively, like every other chip transition) returns
 * [selected] untouched.
 *
 * Returning the *same list instance* for a no-op tap is deliberate: callers can compare identity
 * and skip a redundant `copy`, and the group can never transiently become empty (T-03-18).
 *
 * @param label    the tapped chip's label, in its stored casing
 * @param selected the current selection, in tap order
 * @param mode     which gesture model the picker is rendering
 */
internal fun selectServiceType(
    label: String,
    selected: List<String>,
    mode: SelectionMode,
): List<String> = when (mode) {
    SelectionMode.Multiple -> toggleServiceType(label, selected)
    SelectionMode.Single ->
        if (selected.any { it.equals(label, ignoreCase = true) }) selected else listOf(label)
}

/**
 * The single chip-tap transition: a chip that is selected is removed, any other is appended.
 *
 * Matching is case-insensitive so a `Battery` / `battery` pair can never both enter the selection
 * (T-02-08-02), and removing one keeps the stored casing of the labels that stay.
 */
internal fun toggleServiceType(label: String, selected: List<String>): List<String> {
    val isSelected = selected.any { it.equals(label, ignoreCase = true) }
    return if (isSelected) {
        selected.filterNot { it.equals(label, ignoreCase = true) }
    } else {
        selected + label
    }
}

/**
 * Applies [confirmCustomServiceType] to the form's selection **and** its learned chip list in one
 * `copy`, which is what makes gap 1 structurally impossible to reintroduce.
 */
internal fun ServiceEntryEditUiState.Ready.withConfirmedCustomType(
    label: String,
): ServiceEntryEditUiState.Ready {
    val next = confirmCustomServiceType(
        label = label,
        selected = values.serviceTypes,
        learned = learnedTypes,
    )
    return copy(
        values = values.copy(serviceTypes = next.selected),
        learnedTypes = next.learned,
    )
}

/** Applies [toggleServiceType] to the selection only; the learned chip list is untouched. */
internal fun ServiceEntryEditUiState.Ready.withToggledType(label: String): ServiceEntryEditUiState.Ready =
    copy(values = values.copy(serviceTypes = toggleServiceType(label, values.serviceTypes)))
