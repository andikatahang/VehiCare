package com.vehicare.testing

/**
 * Suppresses a test on the **plain-JVM Android host target only**, leaving it active everywhere else.
 *
 * The one thing this exists for: a `commonTest` scenario that reaches Compose Resources' `getString`
 * (through production code, not a fixture) calls `android.content.res.Resources.getSystem()`, which
 * the stub `android.jar` on `:shared:testAndroidHostTest` throws
 * `RuntimeException("... not mocked")` for. Robolectric would fix it but is a new, unaudited build
 * dependency — see `.planning/STATE.md`.
 *
 * A bare `kotlin.test.Ignore` is the wrong tool here: it lives in `commonTest` and therefore
 * suppresses the test on **every** target, including `iosSimulatorArm64Test` and the instrumented
 * `androidDeviceTest`, both of which resolve resources for real and pass. That silently traded away
 * the only coverage asserting a notification is ever actually posted (Phase 3 verification,
 * 2026-10-24).
 *
 * Use this instead of `@Ignore` whenever the blocker is the JVM resource stub rather than the logic.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
expect annotation class IgnoreOnAndroidHost()
