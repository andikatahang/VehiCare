package com.vehicare.testing

/**
 * On the plain-JVM host target the annotation **is** an ignore: this is the one compilation where
 * `Resources.getSystem()` is unmocked.
 *
 * Aliased to `org.junit.Ignore` rather than `kotlin.test.Ignore` deliberately: on JVM the latter is
 * itself a `typealias` for this same class, and an `actual typealias` must resolve to a class, not
 * to another alias (`ACTUAL_TYPE_ALIAS_NOT_TO_CLASS`). JUnit 4 is on this classpath via
 * `kotlin-test-junit`.
 */
actual typealias IgnoreOnAndroidHost = org.junit.Ignore
