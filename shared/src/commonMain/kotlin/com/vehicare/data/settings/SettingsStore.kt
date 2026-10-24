package com.vehicare.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app-level "service interval (months)" setting (quick task 261024-u9g, D-1).
 *
 * When [readIntervalMonths] returns non-null, that one value **overrides every rule's**
 * `intervalMonths` for evaluation, due-date arithmetic and cycle-key derivation — one universal
 * setting, all vehicles and all service types inclusive. When it returns `null` ("never set"),
 * every rule falls back to its own stored `intervalMonths`. That is the whole contract: the
 * global setting is not a default, it is a replacement.
 *
 * The value is persisted outside SQLDelight on purpose — `SharedPreferences` on Android and
 * `NSUserDefaults` on iOS — so the change touches no migration, no schema and no stored rule row;
 * the `ReminderRule` shape (and every legacy months-only row) stays exactly as it is.
 *
 * [observeIntervalMonths] keeps the Settings screen live: it must emit the current value
 * immediately and again after every `set`/delete, without a manual refresh. A `null` removal is as
 * observable as a non-null write.
 */
interface SettingsStore {

    /** `null` means "the global interval has never been set" (per-rule fallback applies). */
    fun readIntervalMonths(): Long?

    /**
     * Writes the global interval, or clears it when [months] is `null` — clearing is a real,
     * first-class operation because it restores D-1's per-rule fallback.
     */
    fun setIntervalMonths(months: Long?)

    /** The current setting as a flow: current value first, then one emission per change. */
    fun observeIntervalMonths(): Flow<Long?>
}

/**
 * The process-wide [SettingsStore] holder — an exact structural clone of
 * [com.vehicare.data.db.DatabaseProvider]'s `initialize`/`require` contract, for the same reason
 * that holder exists: consumers **outside** the Koin graph must read the same instance the UI
 * observes.
 *
 * The reminder delivery paths (the Android worker build and `ReminderDeliveryFactory`'s lazy
 * interval read) are built outside Koin, so a Koin binding alone cannot reach them; this holder is
 * what the platform entry points install into before Compose starts.
 */
object SettingsProvider {

    private var holder: SettingsStore? = null

    /**
     * Installs the process's single store.
     *
     * @throws IllegalStateException if called twice — a second call would silently swap the store
     *   behind every observer, so it is a programming error rather than a no-op (D-63's rule,
     *   mirrored here so the two holders behave identically).
     */
    fun initialize(store: SettingsStore) {
        check(holder == null) { "SettingsProvider already initialised" }
        holder = store
    }

    /**
     * The store every consumer must share.
     *
     * @throws IllegalStateException with an actionable message when [initialize] was never called —
     *   a missing platform wiring step must not surface as a null dereference at first read.
     */
    fun require(): SettingsStore =
        holder ?: error("SettingsProvider.initialize() was never called")

    /**
     * A **non-throwing, reactive** read for in-graph consumers (the UI ViewModels whose combines
     * re-derive due states): when the platform entry point never initialized the provider — plain
     * unit-test contexts, always — this is a plain `null` flow, which is exactly D-1's "never set"
     * fallback. In production it re-emits on every Settings change, so lists stay live without a
     * poll.
     */
    fun observeIntervalMonthsOrDefault(): Flow<Long?> =
        runCatching { require().observeIntervalMonths() }
            .getOrDefault(MutableStateFlow(null))
}

/**
 * In-memory [StateFlow] spine shared by the platform actuals: each actual keeps the persisted
 * value in sync with this flow, so `observe` fires a manual-refresh-free update on every write.
 * `null` is a normal, held state ("never set" or "cleared") — it is the miss sentinel, never an
 * error indicator.
 */
internal class ObservableSettingsValue(initial: Long?) {

    private val state = MutableStateFlow(initial)

    val current: Long? get() = state.value

    val flow: Flow<Long?> = state.asStateFlow()

    fun set(months: Long?) {
        state.value = months
    }
}

/**
 * Builds the platform's [SettingsStore]. [platformContext] carries the one platform value the
 * implementation needs — an Android `Context` (the **application** context, never an activity),
 * ignored on iOS — the same `Any?` contract `createNotificationScheduler` uses, for the same
 * reason: `commonMain` cannot name a platform type.
 */
expect fun createSettingsStore(platformContext: Any? = null): SettingsStore
