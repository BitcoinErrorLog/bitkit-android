package to.bitkit.paykit.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import to.bitkit.paykit.KeyManager
import to.bitkit.utils.Logger
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PubkyAppFile model matching pubky-app-specs
 */
data class PubkyAppFile(
    val name: String,
    val createdAt: Long, // microseconds
    val src: String,
    val contentType: String,
    val size: Int,
) {
    fun toJson(): String = buildString {
        append("{")
        append("\"name\":\"$name\",")
        append("\"created_at\":$createdAt,")
        append("\"src\":\"$src\",")
        append("\"content_type\":\"$contentType\",")
        append("\"size\":$size")
        append("}")
    }
}

/**
 * Service for uploading images to the homeserver.
 * Follows pubky-app-specs for file storage.
 */
@Singleton
class ImageUploadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directoryService: DirectoryService,
    private val keyManager: KeyManager,
) {
    companion object {
        private const val TAG = "ImageUploadService"
        private const val MAX_IMAGE_SIZE = 1024 // Max dimension in pixels
        private const val JPEG_QUALITY = 80
        
        // Crockford Base32 alphabet
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    }

    /**
     * Upload an image and return the pubky:// URL for use in profile.image
     * @param imageUri The content URI of the image to upload
     * @return The pubky:// URL pointing to the file metadata
     */
    suspend fun uploadProfileImage(imageUri: Uri): Result<String> = runCatching {
        val ownerPubkey = keyManager.getCurrentPublicKeyZ32()
            ?: throw ImageUploadException("No identity configured")

        // 1. Load and resize image
        val imageData = loadAndResizeImage(imageUri)

        // 2. Generate file ID (timestamp-based, Crockford Base32)
        val fileId = generateTimestampId()

        // 3. Upload blob to homeserver
        val blobPath = "/pub/pubky.app/blobs/$fileId"
        directoryService.uploadBlob(blobPath, imageData, "image/jpeg")

        // 4. Create file metadata entry
        val blobUrl = "pubky://$ownerPubkey$blobPath"
        val fileMetadata = PubkyAppFile(
            name = "profile-image.jpg",
            createdAt = System.currentTimeMillis() * 1000, // microseconds
            src = blobUrl,
            contentType = "image/jpeg",
            size = imageData.size,
        )

        val filePath = "/pub/pubky.app/files/$fileId"
        directoryService.uploadFileMetadata(filePath, fileMetadata.toJson())

        // 5. Return the file URL for use in profile
        val fileUrl = "pubky://$ownerPubkey$filePath"
        Logger.info("Uploaded profile image: $fileUrl", context = TAG)

        fileUrl
    }

    /**
     * Upload an image from a Bitmap
     */
    suspend fun uploadProfileImage(bitmap: Bitmap): Result<String> = runCatching {
        val ownerPubkey = keyManager.getCurrentPublicKeyZ32()
            ?: throw ImageUploadException("No identity configured")

        // 1. Resize and compress
        val resizedBitmap = resizeBitmap(bitmap, MAX_IMAGE_SIZE)
        val imageData = compressBitmap(resizedBitmap)

        // 2. Generate file ID
        val fileId = generateTimestampId()

        // 3. Upload blob
        val blobPath = "/pub/pubky.app/blobs/$fileId"
        directoryService.uploadBlob(blobPath, imageData, "image/jpeg")

        // 4. Create file metadata
        val blobUrl = "pubky://$ownerPubkey$blobPath"
        val fileMetadata = PubkyAppFile(
            name = "profile-image.jpg",
            createdAt = System.currentTimeMillis() * 1000,
            src = blobUrl,
            contentType = "image/jpeg",
            size = imageData.size,
        )

        val filePath = "/pub/pubky.app/files/$fileId"
        directoryService.uploadFileMetadata(filePath, fileMetadata.toJson())

        // 5. Return file URL
        val fileUrl = "pubky://$ownerPubkey$filePath"
        Logger.info("Uploaded profile image: $fileUrl", context = TAG)

        fileUrl
    }

    private fun loadAndResizeImage(uri: Uri): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw ImageUploadException("Cannot open image")

        val originalBitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw ImageUploadException("Cannot decode image")
        inputStream.close()

        val resizedBitmap = resizeBitmap(originalBitmap, MAX_IMAGE_SIZE)
        return compressBitmap(resizedBitmap)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxSize = maxOf(width, height)

        if (maxSize <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / maxSize
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun compressBitmap(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Generate a timestamp-based ID in Crockford Base32 (13 characters)
     */
    private fun generateTimestampId(): String {
        val timestamp = System.currentTimeMillis() * 1000 // microseconds
        return encodeCrockfordBase32(timestamp.toULong())
    }

    private fun encodeCrockfordBase32(value: ULong): String {
        val result = StringBuilder()
        var remaining = value

        repeat(13) {
            val index = (remaining and 0x1FuL).toInt()
            result.insert(0, ALPHABET[index])
            remaining = remaining shr 5
        }

        return result.toString()
    }
}

class ImageUploadException(message: String) : Exception(message)

