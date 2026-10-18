package com.vehicare

import platform.Foundation.NSBundle

/**
 * iOS actual: the bundle's short version string (`CFBundleShortVersionString`).
 */
actual val appVersionName: String by lazy {
    NSBundle.mainBundle
        .infoDictionary
        ?.get("CFBundleShortVersionString")
        ?.let { it as? String }
        .orEmpty()
}
