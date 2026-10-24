package com.vehicare.testing

/**
 * No-op on the instrumented target: a real device or emulator resolves Compose Resources for real,
 * so these tests run (and are expected to pass) under `connectedAndroidDeviceTest`.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
actual annotation class IgnoreOnAndroidHost actual constructor()
