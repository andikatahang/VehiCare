package com.vehicare.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.ImageFormat
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.compressImage
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.toKotlinxIoPath
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CancellationException
import kotlinx.io.files.SystemFileSystem

/**
 * The app's single photo-filesystem seam.
 *
 * A vehicle's photo is stored as a file inside app-private storage and the database keeps only the
 * **relative** location (D-05) — never a container-dependent one, because the iOS container's
 * location changes between installs and a stored location would render as a broken image after an
 * update (RESEARCH Pitfall 3).
 *
 * This object is the only place in the app that touches photo files: [persist], [resolve] and
 * [delete] live here and nothing else constructs a location from the files directory. Do not create
 * a second storage object anywhere.
 */
object PhotoStorage {

    /** Name of the app-private directory that holds every persisted vehicle photo. */
    private const val VEHICLES_DIRECTORY = "vehicles"

    /** Name of the app-private directory that holds every persisted service entry photo. */
    const val SERVICE_ENTRY_DIRECTORY = "service_entries"

    /**
     * Longest side a persisted photo keeps, in pixels.
     *
     * A gallery photo is several thousand pixels wide while the form renders it inside a 16:9 frame,
     * so anything larger only slows later list renders down (T-01-48).
     */
    private const val MAX_IMAGE_DIMENSION = 1280

    /** JPEG quality of the compressed copy written to app storage. */
    private const val JPEG_QUALITY = 80

    /**
     * Copies [picked] into app-private storage under `directory/[fileName]` and returns the
     * **relative** location the database stores.
     *
     * The bytes are compressed (JPEG, bounded to [MAX_IMAGE_DIMENSION]) before they are written, so
     * a full-resolution gallery photo never lands in storage untouched. The picker's temporary file
     * is only read from: its location goes stale the moment the process dies, so only our own copy
     * is ever referenced (T-01-51).
     *
     * A pick whose bytes are not a decodable image is stored verbatim instead of failing — refusing
     * to attach the user's selection would be a worse outcome than skipping the downscale.
     *
     * @param directory the app-private subdirectory to store the photo in (default: VEHICLES_DIRECTORY for vehicle photos)
     */
    suspend fun persist(picked: PlatformFile, fileName: String, directory: String = VEHICLES_DIRECTORY): String {
        val relative = "$directory/$fileName"
        val source = picked.readBytes()
        resolve(directory).createDirectories()
        resolve(relative).write(compress(source))
        return relative
    }

    /**
     * Resolves a stored relative location against the app's files directory.
     *
     * The base directory is read on every call rather than captured once, so the value is always
     * the directory of the *current* install/launch.
     */
    fun resolve(relativePath: String): PlatformFile =
        PlatformFile(FileKit.filesDir, relativePath)

    /**
     * Removes the file behind a stored relative location.
     *
     * Non-suspend on purpose: the ViewModel calls it while a form is being torn down, where no
     * coroutine scope exists any more. A null, blank or already-absent location is a normal no-op —
     * abandoning a form must never fail.
     */
    fun delete(relativePath: String?) {
        val relative = relativePath?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            SystemFileSystem.delete(
                path = resolve(relative).toKotlinxIoPath(),
                mustExist = false,
            )
        }
    }

    /**
     * Returns the bytes to store: the compressed copy when the pick really is a decodable image,
     * otherwise the picked bytes unchanged.
     */
    private suspend fun compress(source: ByteArray): ByteArray =
        try {
            FileKit.compressImage(
                bytes = source,
                imageFormat = ImageFormat.JPEG,
                quality = JPEG_QUALITY,
                maxWidth = MAX_IMAGE_DIMENSION,
                maxHeight = MAX_IMAGE_DIMENSION,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            source
        }
}
