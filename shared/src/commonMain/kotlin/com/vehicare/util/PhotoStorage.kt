package com.vehicare.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.filesDir

/**
 * The app's single photo-filesystem seam.
 *
 * A vehicle's photo is stored as a file inside app-private storage and the database keeps only the
 * **relative** path (D-05) — never a container-dependent location, because the iOS container's
 * location changes between installs and a stored location would render as a broken image after an
 * update (RESEARCH Pitfall 3).
 *
 * This object is authored in two steps: this plan owns only [resolve] (the read side the list card
 * needs to display a thumbnail); plan 01-08 adds the write/delete side (`persist`, `delete` and the
 * downscale step) to this same object. Do not create a second storage object anywhere.
 */
object PhotoStorage {

    /**
     * Resolves a stored relative path against the app's files directory.
     *
     * The base directory is read on every call rather than captured once, so the value is always
     * the directory of the *current* install/launch.
     */
    fun resolve(relativePath: String): PlatformFile =
        PlatformFile(FileKit.filesDir, relativePath)
}
