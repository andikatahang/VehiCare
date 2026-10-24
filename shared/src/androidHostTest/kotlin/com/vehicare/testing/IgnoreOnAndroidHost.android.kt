package com.vehicare.testing

/**
 * On the plain-JVM host target the annotation **is** an ignore: this is the one compilation where
 * `Resources.getSystem()` is unmocked.
 */
actual typealias IgnoreOnAndroidHost = kotlin.test.Ignore
