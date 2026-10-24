package com.vehicare.testing

/**
 * No-op on iOS: Kotlin/Native has no `android.jar` stub in the way, so these tests run and pass on
 * `iosSimulatorArm64Test` — verified 2026-10-24 (318 tests, 0 skipped, 0 failures).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
actual annotation class IgnoreOnAndroidHost actual constructor()
