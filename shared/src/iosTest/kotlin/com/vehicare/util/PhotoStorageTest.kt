package com.vehicare.util

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.delete as fileKitDelete
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Proves [PhotoStorage] against the real simulator filesystem, not a mock: the bytes of a picked
 * file really land inside the app's own storage, the value handed to the database is a **relative**
 * path that survives the container moving, and deletion really removes a file while tolerating one
 * that is already gone.
 *
 * Every stored file is written under a unique name and removed again, and the scratch sources live
 * in the cache directory, so nothing this class does outlives the run.
 */
class PhotoStorageTest {

    private val runId = Random.nextLong(0, Long.MAX_VALUE)
    private var counter = 0
    private val storedPaths = mutableListOf<String>()
    private val sourceFiles = mutableListOf<PlatformFile>()

    @AfterTest
    fun tearDown() = runTest {
        storedPaths.forEach { PhotoStorage.delete(it) }
        storedPaths.clear()
        sourceFiles.forEach { it.fileKitDelete(mustExist = false) }
        sourceFiles.clear()
    }

    @Test
    fun persistCopiesThePickedBytesAndReturnsARelativePath() = runTest {
        val bytes = syntheticBytes(seed = 1)
        val source = sourceFile(bytes)
        val fileName = nextFileName()

        val returned = PhotoStorage.persist(source, fileName)

        assertEquals("vehicles/$fileName", returned)
        assertFalse(returned.startsWith("/"), "the stored value must never be an absolute location")
        assertFalse(
            returned.contains(FileKit.filesDir.path),
            "the stored value must never contain the container-dependent base directory",
        )
        val stored = PhotoStorage.resolve(returned)
        storedPaths += returned
        assertTrue(stored.exists(), "the photo must really exist inside app storage")
        assertContentEquals(bytes, stored.readBytes(), "the picked bytes must be copied, never referenced")
    }

    @Test
    fun persistCreatesTheStorageDirectoryWhenItIsAbsent() = runTest {
        PhotoStorage.resolve(directoryName).fileKitDelete(mustExist = false, recursively = true)
        assertFalse(PhotoStorage.resolve(directoryName).exists(), "precondition: the directory is absent")

        val bytes = syntheticBytes(seed = 2)
        val returned = PhotoStorage.persist(sourceFile(bytes), nextFileName())

        storedPaths += returned
        assertTrue(PhotoStorage.resolve(returned).exists(), "a fresh install has no directory yet")
        assertContentEquals(bytes, PhotoStorage.resolve(returned).readBytes())
    }

    @Test
    fun deleteRemovesTheStoredFile() = runTest {
        val returned = PhotoStorage.persist(sourceFile(syntheticBytes(seed = 3)), nextFileName())
        assertTrue(PhotoStorage.resolve(returned).exists())

        PhotoStorage.delete(returned)

        assertFalse(PhotoStorage.resolve(returned).exists(), "the superseded file must be gone")
    }

    @Test
    fun deleteToleratesNullBlankAndAnAlreadyAbsentFile() = runTest {
        val returned = PhotoStorage.persist(sourceFile(syntheticBytes(seed = 4)), nextFileName())
        PhotoStorage.delete(returned)

        // The normal case when a form is abandoned: nothing to remove, and no failure either.
        PhotoStorage.delete(returned)
        PhotoStorage.delete(null)
        PhotoStorage.delete("")
        PhotoStorage.delete("   ")
    }

    @Test
    fun persistAndResolveKeepTwoStoredPhotosDistinct() = runTest {
        val firstBytes = syntheticBytes(seed = 5)
        val secondBytes = syntheticBytes(seed = 6)

        val first: String = PhotoStorage.persist(sourceFile(firstBytes), nextFileName())
        val second: String = PhotoStorage.persist(sourceFile(secondBytes), nextFileName())
        storedPaths += first
        storedPaths += second

        val firstFile = PhotoStorage.resolve(first)
        val secondFile = PhotoStorage.resolve(second)

        assertNotEquals(first, second)
        assertTrue(firstFile.exists() && secondFile.exists(), "both resolves must hit the live directory")
        assertContentEquals(firstBytes, firstFile.readBytes())
        assertContentEquals(secondBytes, secondFile.readBytes())
    }

    @Test
    fun persistCompressesAPickedImageIntoAJpeg() = runTest {
        val returned = PhotoStorage.persist(sourceFile(onePixelPng()), nextFileName())
        storedPaths += returned

        val stored = PhotoStorage.resolve(returned).readBytes()

        assertTrue(stored.size > 2)
        assertEquals(0xFF, stored[0].toInt() and 0xFF, "compression must produce JPEG data")
        assertEquals(0xD8, stored[1].toInt() and 0xFF, "compression must produce JPEG data")
    }

    @Test
    @OptIn(ExperimentalForeignApi::class)
    fun persistCapsTheStoredImageDimension() = runTest {
        val sourceWidth = 2400
        val sourceHeight = 1600
        val returned = PhotoStorage.persist(
            sourceFile(generatedJpeg(sourceWidth, sourceHeight)),
            nextFileName(),
        )
        storedPaths += returned

        val stored = PhotoStorage.resolve(returned).readBytes().toUIImage()
        val storedWidth = stored.size.useContents { width }.toInt()
        val storedHeight = stored.size.useContents { height }.toInt()

        assertTrue(
            storedWidth <= 1280 && storedHeight <= 1280,
            "a full-resolution gallery photo must be downscaled, was ${storedWidth}x$storedHeight",
        )
        assertTrue(
            storedWidth < sourceWidth || storedHeight < sourceHeight,
            "the stored photo must be smaller than the picked one, was ${storedWidth}x$storedHeight",
        )
    }

    private companion object {
        const val directoryName = "vehicles"
    }

    private fun nextFileName(): String = "01-08-$runId-${counter++}.jpg"

    /** Bytes that are deliberately not a decodable image: they exist only to be copied verbatim. */
    private fun syntheticBytes(seed: Int): ByteArray = ByteArray(96) { index -> (index * seed).toByte() }

    private suspend fun sourceFile(bytes: ByteArray): PlatformFile {
        val file = FileKit.cacheDir / "01-08-source-$runId-${counter++}"
        file.write(bytes)
        sourceFiles += file
        return file
    }

    /** The smallest valid PNG: 1x1 RGBA, verified decodable before it is used as a picked image. */
    private fun onePixelPng(): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(), 0x89.toByte(),
        0x00, 0x00, 0x00, 0x0B, 0x49, 0x44, 0x41, 0x54,
        0x78, 0xDA.toByte(), 0x63, 0x60, 0x00, 0x02, 0x00, 0x00, 0x05, 0x00, 0x01,
        0xE9.toByte(), 0xFA.toByte(), 0xDC.toByte(), 0xD8.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
        0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
    )

    /** A real photo-sized JPEG, so the downscale step has something to actually shrink. */
    @OptIn(ExperimentalForeignApi::class)
    private fun generatedJpeg(width: Int, height: Int): ByteArray {
        UIGraphicsBeginImageContextWithOptions(
            CGSizeMake(width.toDouble(), height.toDouble()),
            false,
            1.0,
        )
        val image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        val data = UIImageJPEGRepresentation(requireNotNull(image), 0.9)
        return requireNotNull(data).toByteArray()
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun ByteArray.toUIImage(): UIImage =
        UIImage(data = usePinned { pinned -> NSData.create(pinned.addressOf(0), size.toULong()) })

    @OptIn(ExperimentalForeignApi::class)
    private fun NSData.toByteArray(): ByteArray {
        val byteCount = length.toInt()
        if (byteCount == 0) return ByteArray(0)
        val out = ByteArray(byteCount)
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
        return out
    }
}
