package com.vehicare.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The three inputs [NotificationPermissionPolicy] needs, and nothing else — no derived flag is
 * stored here, so none of them can drift from these three primitives.
 */
internal data class NotificationPermissionUiState(
    val permission: NotificationPermission,
    val hasRequestedSystemPrompt: Boolean,
    val hasAutoExplained: Boolean,
)

/**
 * The pure askability gate (D-54/D-61). Every function is an exhaustive `when` over
 * [NotificationPermission]'s three members — there is no `else` branch to silently swallow a
 * fourth one later.
 *
 * Truth-table highlights, the three non-obvious rows:
 * - `NOT_DETERMINED` with `hasRequestedSystemPrompt = true` stays askable: the process died
 *   during the OS prompt and the OS still permits a first ask.
 * - `DENIED` with `hasRequestedSystemPrompt = false` is the Android-before-the-first-ask case —
 *   Android has no `NOT_DETERMINED`, so this must not read as a real denial to a user who was
 *   never asked.
 * - [shouldAutoExplain] is `false` the instant `hasAutoExplained` is `true`, whatever the
 *   permission state. That one fact is what makes the automatic ask exactly one per install
 *   while [isAskable] keeps a manual tap alive for the rest of an undenied install.
 */
internal object NotificationPermissionPolicy {

    /** May the OS still be asked at all? What a **tap** consults; it never changes on its own. */
    fun isAskable(permission: NotificationPermission, hasRequestedSystemPrompt: Boolean): Boolean =
        when (permission) {
            NotificationPermission.NOT_DETERMINED -> true
            NotificationPermission.DENIED -> !hasRequestedSystemPrompt
            NotificationPermission.GRANTED -> false
        }

    /**
     * May the app open the explanation dialog *by itself* one more time? The whole 2026-10-23
     * amendment, in one line: an automatic ask is spent the moment it has been shown once.
     */
    fun shouldAutoExplain(
        permission: NotificationPermission,
        hasRequestedSystemPrompt: Boolean,
        hasAutoExplained: Boolean,
    ): Boolean = !hasAutoExplained && isAskable(permission, hasRequestedSystemPrompt)

    /** Is the permission surface (banner/row) shown? Simply "not granted" plus an enabled rule. */
    fun shouldShowSurface(permission: NotificationPermission, hasEnabledRule: Boolean): Boolean =
        permission != NotificationPermission.GRANTED && hasEnabledRule
}

/**
 * The one process-wide owner of the platform permission read (D-61's "re-checked on every
 * foreground, nothing reactive").
 *
 * [state] is `null` until [refresh] has completed once — `null` therefore means "not read yet",
 * never "no permission", and every surface must hide while it is `null`.
 */
internal class NotificationPermissionCoordinator(
    private val scheduler: NotificationScheduler,
    private val store: NotificationPermissionStore,
    private val requester: NotificationPermissionRequester,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<NotificationPermissionUiState?>(null)
    val state: StateFlow<NotificationPermissionUiState?> = _state

    /** Single-flight guard: a double tap while a request is already in flight is a no-op. */
    private var requesting = false

    /** Re-reads the platform state. Called once on first composition and again on every foreground. */
    fun refresh() {
        scope.launch {
            _state.value = NotificationPermissionUiState(
                permission = scheduler.permissionState(),
                hasRequestedSystemPrompt = store.hasRequestedSystemPrompt(),
                hasAutoExplained = store.hasAutoExplained(),
            )
        }
    }

    /**
     * Persists the automatic-explanation bit **before** it updates [state], so the persisted
     * fact can never lag the UI — a crash between the two would otherwise be able to re-open the
     * dialog on the next launch.
     */
    fun markAutoExplained() {
        scope.launch {
            store.markAutoExplained()
            _state.update { it?.copy(hasAutoExplained = true) }
        }
    }

    /**
     * Runs the platform ask if (and only if) the current state is askable and no request is
     * already in flight — the guard that makes a double tap harmless.
     *
     * The system-prompt bit is written **before** the platform call so a process death during
     * the OS sheet cannot buy a second ask. [state] is updated from the callback so the surface
     * corrects itself the moment the prompt resolves, before any lifecycle event. A `GRANTED`
     * result re-enters plan 03-07's sweep through the non-suspend, fire-and-forget
     * [NotificationScheduler.evaluateNow] — that sweep refuses to write any marker without
     * permission, so the first add's pre-dialog sweep delivered nothing and this is the one that
     * actually arms and delivers the cycle it skipped. The call happens before [onSettled] so a
     * caller that closes the dialog cannot delay it, and it is deliberately not awaited —
     * delivery stays fire-and-forget.
     */
    fun requestPermission(onSettled: () -> Unit = {}) {
        val current = _state.value ?: return
        if (requesting ||
            !NotificationPermissionPolicy.isAskable(current.permission, current.hasRequestedSystemPrompt)
        ) {
            return
        }

        store.markSystemPromptRequested()
        requesting = true
        requester.request { result ->
            _state.value = NotificationPermissionUiState(
                permission = result,
                hasRequestedSystemPrompt = true,
                hasAutoExplained = store.hasAutoExplained(),
            )
            requesting = false
            if (result == NotificationPermission.GRANTED) scheduler.evaluateNow()
            onSettled()
        }
    }

    /** Opens the OS's own notification settings for this app. */
    fun openSettings() {
        scope.launch { scheduler.openNotificationSettings() }
    }
}

/**
 * Provided once in `App.kt`'s shell, around the `NavHost`. `staticCompositionLocalOf` because
 * the instance's identity never changes for the life of the composition.
 */
internal val LocalNotificationPermissionCoordinator =
    staticCompositionLocalOf<NotificationPermissionCoordinator> {
        error("No NotificationPermissionCoordinator provided — App.kt must provide one around the NavHost")
    }

/**
 * Builds the one coordinator for this composition and performs its explicit initial read.
 *
 * The initial read is an explicit `LaunchedEffect(Unit)` rather than relying on
 * `LifecycleEventEffect`'s observer replay — which is exactly why [NotificationPermissionCoordinator.state]'s
 * `null` can safely mean "not read yet" instead of "no permission".
 */
@Composable
internal fun rememberNotificationPermissionCoordinator(
    scheduler: NotificationScheduler,
): NotificationPermissionCoordinator {
    val requester = rememberNotificationPermissionRequester(scheduler)
    val store = rememberNotificationPermissionStore()
    val scope = rememberCoroutineScope()
    val coordinator = remember(scheduler, requester, store, scope) {
        NotificationPermissionCoordinator(
            scheduler = scheduler,
            store = store,
            requester = requester,
            scope = scope,
        )
    }
    LaunchedEffect(Unit) { coordinator.refresh() }
    return coordinator
}
