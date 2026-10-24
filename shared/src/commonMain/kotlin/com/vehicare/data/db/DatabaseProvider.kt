package com.vehicare.data.db

import com.vehicare.db.VehiCareDatabase

/**
 * The process-wide [VehiCareDatabase] holder (D-63).
 *
 * Each platform entry point calls [initialize] **exactly once per process, before Compose starts**:
 * `VehiCareApp.onCreate` on Android and `MainViewController` on iOS. No other code may construct a
 * [DatabaseFactory] — the Koin graph reads this holder via [require], and the future
 * `CoroutineWorker` (which has no Koin container at all) reads it too.
 *
 * The reason this holder exists rather than a Koin binding alone: SQLDelight's change-notification
 * listener registry lives on the **driver instance**. A second `VehiCareDatabase` over the same file
 * would write successfully while the UI's `asFlow()` queries never emit — the dashboard would show
 * stale due states until a restart (03-RESEARCH Pattern 1 / Pitfall 1). One driver, one holder.
 *
 * Plain `commonMain` on purpose: there is nothing platform-specific here, so there are no
 * `DatabaseProvider.android.kt` / `.ios.kt` actuals to keep in sync.
 */
object DatabaseProvider {

    private var holder: DatabaseFactory? = null

    /**
     * Installs the process's single database.
     *
     * @throws IllegalStateException if called twice — a second call would silently replace the
     *   driver every consumer is already observing, so it is a programming error rather than a
     *   no-op.
     */
    fun initialize(factory: DatabaseFactory) {
        check(holder == null) { "DatabaseProvider already initialised" }
        holder = factory
    }

    /**
     * The database every repository, ViewModel and worker must share.
     *
     * @throws IllegalStateException with an actionable message when [initialize] was never called —
     *   a missing platform wiring step must not surface as a null dereference at first query.
     */
    fun require(): VehiCareDatabase =
        holder?.database ?: error("DatabaseProvider.initialize() was never called")
}
