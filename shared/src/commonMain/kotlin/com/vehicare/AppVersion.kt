package com.vehicare

/**
 * The human-readable app version shown in the Settings "App version" row.
 *
 * The accessor is shared so no screen hardcodes a version literal; the platform actuals read the
 * version the OS reports for the installed app (Android) or the bundle (iOS). Deliberately no
 * dependency and no `buildConfig` enablement — `:shared` declares no `versionName`, so the value
 * must come from the installed package itself.
 */
expect val appVersionName: String
