package com.vehicare.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.data.settings.SettingsStore
import com.vehicare.notifications.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The D-17 Settings placeholder's screen state (261024-u9g, D-1).
 *
 * The app-level "Service interval (months)" setting is one universal value: when set, it
 * overrides every reminder rule's own `intervalMonths` for evaluation, due-date arithmetic and
 * cycle-key derivation; when it has never been set (blank field), every rule falls back to its own
 * stored value — that is exactly what the hint sentence says. Saving fires
 * [NotificationScheduler.evaluateNow] so the delivery paths immediately re-derive the cycles: the
 * effective months feed the cycle key, so any value change (and clearing) re-arms every previously
 * armed/notified cycle on both platforms.
 *
 * Validation mirrors the old editor's month cap (C-34's 1..1200): an invalid value never writes —
 * the store keeps its previous value and only the error state flips.
 *
 * @param settingsStore the process-wide store (`SettingsProvider` behind the Koin binding)
 * @param scheduler the fire-and-forget re-evaluation seam the repository hooks already use
 */
class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val scheduler: NotificationScheduler,
) : ViewModel() {

    /** The raw display cap (the old editor's): 3 digits keeps 1..1200 comfortably inside. */
    internal companion object {
        internal const val MAX_DIGITS = 4
        internal const val MAX_MONTHS = 1200L
    }

    /** Digits only, capped — mirrors `ReminderFormValidation`'s input-boundary convention. */
    fun onIntervalChange(raw: String): String = raw.trim().filter { it.isDigit() }.take(MAX_DIGITS)

    /**
     * The field content, blank when the setting has never been set. Live from the store's observe
     * flow: an external change (a second process, a cleared preference) arrives without polling.
     */
    val intervalText: StateFlow<String> =
        settingsStore.observeIntervalMonths()
            .map { stored -> stored?.toString().orEmpty() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = "",
            )

    private val _intervalError = MutableStateFlow(false)

    /** True after an invalid [save]; the screen shows `error_interval_months_universal`. */
    val intervalError: StateFlow<Boolean> = _intervalError.asStateFlow()

    private val _savedFlash = MutableStateFlow(false)

    /** True after a successful write; the screen shows `settings_service_interval_saved`. */
    val savedFlash: StateFlow<Boolean> = _savedFlash.asStateFlow()

    /**
     * Saves the interval typed in [text]: blank → clear (per-rule fallback restored), otherwise a
     * value in 1..1200. An invalid input — non-numeric junk or out of range — exposes the error
     * state, writes nothing, and never fires the scheduler.
     */
    fun save(text: String) {
        val trimmed = text.trim()
        val blank = trimmed.isEmpty()
        // Blank means "clear the universal interval"; anything else needs a number in 1..1200 —
        // an unparsable value is an error, never a silent clear.
        val parsed = if (blank) null else trimmed.toLongOrNull()
        val valid = blank || (parsed != null && parsed in 1..MAX_MONTHS)

        _intervalError.value = !valid
        if (!valid) return

        settingsStore.setIntervalMonths(parsed)
        // Fire-and-forget re-arm (same seam the repository hooks use): the new effective months
        // change the cycle keys, so every previously armed/notified cycle re-arms per D-1.
        scheduler.evaluateNow()
        _savedFlash.value = true
    }

    /** Retires the saved confirmation after the screen has shown it (or the field changed). */
    fun onSavedShown() {
        if (_savedFlash.value) _savedFlash.value = false
    }

    /** Retires the error state once the screen has surfaced it. */
    fun onErrorShown() {
        if (_intervalError.value) _intervalError.value = false
    }
}
