package eu.kanade.tachiyomi.data.connections.discord

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import eu.kanade.tachiyomi.network.NetworkHelper
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy

private const val UGUU_URI = "https://uguu.se/upload.php?output=text"
private const val CATBOX_URI = "https://catbox.moe/user/api.php"
private const val TAG = "DiscordImageUploader"
private const val MAX_COVER_BYTES = 10 * 1024 * 1024

internal object DiscordImageUploader {

    private val uploadCache = ConcurrentHashMap<String, String>()
    private val networkHelper: NetworkHelper by injectLazy()

    /**
     * Uploads a local content://, file:// URI or absolute file path
     * and returns its public URL.
     */
    suspend fun resolveImageUrl(context: Context, localUri: String): String? =
        withContext(Dispatchers.IO) {
            val file = if (
                localUri.startsWith("content://") ||
                localUri.startsWith("file://")
            ) {
                null
            } else {
                File(localUri)
            }

            val cacheKey = if (file != null) {
                "${file.absolutePath}:${file.lastModified()}"
            } else {
                localUri
            }

            uploadCache[cacheKey]?.let { return@withContext it }

            val bytes = try {
                if (localUri.startsWith("content://") || localUri.startsWith("file://")) {
                    context.contentResolver
                        .openInputStream(Uri.parse(localUri))
                        ?.use { it.readBytes() }
                } else {
                    file?.readBytes()
                }
            } catch (e: Exception) {
                Logger.w(TAG) {
                    "Failed to read local cover: ${e.message}"
                }
                null
            }

            val validBytes = bytes?.takeIf {
                it.isNotEmpty() && it.size <= MAX_COVER_BYTES
            } ?: run {
                if (bytes != null) {
                    Logger.w(TAG) {
                        "Cover rejected: invalid or larger than 10 MB"
                    }
                }
                return@withContext null
            }

            val uploaded = uploadToUguu(validBytes)
                ?: uploadToCatbox(validBytes)

            uploaded?.let {
                uploadCache[cacheKey] = it
            }

            uploaded
        }

    /**
     * Uploads a local file and returns its public URL.
     */
    suspend fun resolveImageUrl(context: Context, file: File): String? =
        withContext(Dispatchers.IO) {
            val cacheKey = "${file.absolutePath}:${file.lastModified()}"

            uploadCache[cacheKey]?.let { return@withContext it }

            val bytes = try {
                file.readBytes()
            } catch (e: Exception) {
                Logger.w(TAG) {
                    "Failed to read cover file: ${e.message}"
                }
                return@withContext null
            }

            val validBytes = bytes.takeIf {
                it.isNotEmpty() && it.size <= MAX_COVER_BYTES
            } ?: run {
                Logger.w(TAG) {
                    "Cover rejected: invalid or larger than 10 MB"
                }
                return@withContext null
            }

            val uploaded = uploadToUguu(validBytes)
                ?: uploadToCatbox(validBytes)

            uploaded?.let {
                uploadCache[cacheKey] = it
            }

            uploaded
        }

    private fun uploadToUguu(bytes: ByteArray): String? = try {
        val extension = extensionFor(bytes)
        val mediaType = "image/$extension".toMediaTypeOrNull()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "files[]",
                "cover.$extension",
                bytes.toRequestBody(mediaType),
            )
            .build()

        networkHelper.client
            .newCall(
                Request.Builder()
                    .url(UGUU_URI)
                    .post(body)
                    .build(),
            )
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    Logger.w(TAG) {
                        "uguu.se upload failed: HTTP ${response.code}"
                    }
                    return@use null
                }

                response.body
                    ?.string()
                    ?.trim()
                    ?.takeIf {
                        it.startsWith("http://") || it.startsWith("https://")
                    }
            }
    } catch (e: Exception) {
        Logger.w(TAG) {
            "uguu.se upload failed: ${e.message}"
        }
        null
    }

    private fun uploadToCatbox(bytes: ByteArray): String? = try {
        val extension = extensionFor(bytes)
        val mediaType = "image/$extension".toMediaTypeOrNull()

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "reqtype",
                "fileupload",
            )
            .addFormDataPart(
                "fileToUpload",
                "cover.$extension",
                bytes.toRequestBody(mediaType),
            )
            .build()

        networkHelper.client
            .newCall(
                Request.Builder()
                    .url(CATBOX_URI)
                    .post(body)
                    .build(),
            )
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    Logger.w(TAG) {
                        "catbox.moe upload failed: HTTP ${response.code}"
                    }
                    return@use null
                }

                response.body
                    ?.string()
                    ?.trim()
                    ?.takeIf {
                        it.startsWith("http://") || it.startsWith("https://")
                    }
            }
    } catch (e: Exception) {
        Logger.w(TAG) {
            "catbox.moe upload failed: ${e.message}"
        }
        null
    }

    private fun extensionFor(bytes: ByteArray): String = when {
        // WebP
        bytes.size >= 12 &&
            String(bytes, 0, 4, Charsets.ISO_8859_1) == "RIFF" &&
            String(bytes, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> "webp"

        // PNG
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() -> "png"

        // JPEG
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "jpeg"

        // Fallback
        else -> "png"
    }
}
