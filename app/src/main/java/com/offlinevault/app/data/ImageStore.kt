package com.offlinevault.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.offlinevault.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Images are copied into app-private storage and re-encoded, which strips source
 * metadata (EXIF, GPS, etc.). Filenames are random UUIDs, never account names.
 * Pending files live in the private cache until the account is saved.
 */
class ImageStore(private val context: Context) {
    private val imagesDir: File = File(context.filesDir, "account_images").apply { mkdirs() }
    private val pendingDir: File = File(context.cacheDir, "pending_images").apply { mkdirs() }
    private val cameraDir: File = File(context.cacheDir, "camera").apply { mkdirs() }

    init {
        // Any pending image is an abandoned draft after a process restart.
        clearPendingDirectory()
    }

    fun createCameraTempFile(): File {
        cameraDir.mkdirs()
        return File(cameraDir, "${UUID.randomUUID()}.jpg")
    }

    suspend fun importToPending(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = try {
            decodeBounded(uri)
        } finally {
            // A capture placed in the private camera cache is consumed once.
            deleteCameraTempFor(uri)
        } ?: throw IllegalArgumentException("Unsupported image")
        val hasAlpha = bitmap.hasAlpha()
        val extension = if (hasAlpha) "png" else "jpg"
        val filename = "${UUID.randomUUID()}.$extension"
        val target = File(pendingDir, filename)
        try {
            FileOutputStream(target).use { output ->
                val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                check(bitmap.compress(format, 90, output)) { "Could not encode image" }
            }
        } finally {
            bitmap.recycle()
        }
        Constants.PENDING_IMAGE_PREFIX + filename
    }

    suspend fun commitPending(pendingRef: String): String = withContext(Dispatchers.IO) {
        val filename = pendingRef.removePrefix(Constants.PENDING_IMAGE_PREFIX)
        require(isSafeFilename(filename)) { "Invalid image reference" }
        val source = File(pendingDir, filename)
        check(source.exists()) { "Pending image is missing" }
        val target = File(imagesDir, filename)
        imagesDir.mkdirs()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        filename
    }

    fun resolveFile(ref: String?): File? {
        if (ref.isNullOrBlank() || ref.startsWith(Constants.BUILTIN_ICON_PREFIX)) return null
        val isPending = ref.startsWith(Constants.PENDING_IMAGE_PREFIX)
        val filename = ref.removePrefix(Constants.PENDING_IMAGE_PREFIX)
        if (!isSafeFilename(filename)) return null
        val file = if (isPending) File(pendingDir, filename) else File(imagesDir, filename)
        return file.takeIf { it.exists() }
    }

    fun delete(ref: String?) {
        if (ref.isNullOrBlank()) return
        if (ref.startsWith(Constants.BUILTIN_ICON_PREFIX)) return
        val isPending = ref.startsWith(Constants.PENDING_IMAGE_PREFIX)
        val filename = ref.removePrefix(Constants.PENDING_IMAGE_PREFIX)
        if (!isSafeFilename(filename)) return
        val file = if (isPending) File(pendingDir, filename) else File(imagesDir, filename)
        runCatching { file.delete() }
    }

    suspend fun readImageBytes(ref: String?): ByteArray? = withContext(Dispatchers.IO) {
        if (ref.isNullOrBlank() || ref.startsWith(Constants.BUILTIN_ICON_PREFIX)) return@withContext null
        val file = resolveFile(ref) ?: return@withContext null
        runCatching { file.readBytes() }.getOrNull()
    }

    suspend fun writeRestoredImage(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val extension = when {
            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> "png"

            else -> "jpg"
        }
        val filename = "${UUID.randomUUID()}.$extension"
        imagesDir.mkdirs()
        File(imagesDir, filename).writeBytes(bytes)
        filename
    }

    fun clearPendingDirectory() {
        pendingDir.listFiles()?.forEach { runCatching { it.delete() } }
        cameraDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    suspend fun deleteAllImages() = withContext(Dispatchers.IO) {
        imagesDir.listFiles()?.forEach { runCatching { it.delete() } }
        pendingDir.listFiles()?.forEach { runCatching { it.delete() } }
        cameraDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private fun decodeBounded(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val largest = max(width, height)
                if (largest > Constants.MAX_IMAGE_DIMENSION) {
                    val scale = Constants.MAX_IMAGE_DIMENSION.toFloat() / largest.toFloat()
                    decoder.setTargetSize(
                        max(1, (width * scale).toInt()),
                        max(1, (height * scale).toInt()),
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (
                bounds.outWidth / (sample * 2) >= Constants.MAX_IMAGE_DIMENSION ||
                bounds.outHeight / (sample * 2) >= Constants.MAX_IMAGE_DIMENSION
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                }
            }.getOrNull()
            val oriented = applyExifOrientation(decoded, orientation)
            if (oriented !== decoded) decoded.recycle()
            resizeIfNeeded(oriented)
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int?): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun resizeIfNeeded(bitmap: Bitmap): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= Constants.MAX_IMAGE_DIMENSION) return bitmap
        val scale = Constants.MAX_IMAGE_DIMENSION.toFloat() / largest.toFloat()
        val width = max(1, (bitmap.width * scale).toInt())
        val height = max(1, (bitmap.height * scale).toInt())
        val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (resized !== bitmap) bitmap.recycle()
        return resized
    }

    private fun isSafeFilename(value: String): Boolean =
        value.isNotBlank() && value == File(value).name && !value.contains("..") &&
            value.length <= 128

    private fun deleteCameraTempFor(uri: Uri) {
        if (uri.authority != "${context.packageName}.fileprovider") return
        val filename = uri.lastPathSegment ?: return
        if (!isSafeFilename(filename)) return
        runCatching { File(cameraDir, filename).delete() }
    }

    @Suppress("unused")
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        val target = min(width, height)
        while (target / sample > Constants.MAX_IMAGE_DIMENSION) sample *= 2
        return sample
    }
}
