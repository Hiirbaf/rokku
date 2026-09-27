package eu.kanade.tachiyomi.data.connections.discord

import android.content.Context
import android.net.Uri
import android.util.Log
import eu.kanade.tachiyomi.network.NetworkHelper
import java.io.File
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

internal object DiscordImageUploader {

    // Cachea por URI local -> URL pública subida, para no volver a subir la misma
    // portada en cada cambio de página/capítulo.
    private val uploadCache = mutableMapOf<String, String>()
    private val networkHelper: NetworkHelper by injectLazy()

    suspend fun resolveImageUrl(context: Context, localUri: String): String? = withContext(Dispatchers.IO) {
        uploadCache[localUri]?.let { return@withContext it }

        val bytes = try {
            context.contentResolver.openInputStream(Uri.parse(localUri))?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read local cover: ${e.message}", e)
            null
        } ?: return@withContext null

        val uploaded = uploadToUguu(bytes) ?: uploadToCatbox(bytes)
        uploaded?.let { uploadCache[localUri] = it }
        uploaded
    }

    suspend fun resolveImageUrl(context: Context, file: File): String? = withContext(Dispatchers.IO) {
        val cacheKey = file.absolutePath
        uploadCache[cacheKey]?.let { return@withContext it }

        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read cover file: ${e.message}", e)
            return@withContext null
        }

        val uploaded = uploadToUguu(bytes) ?: uploadToCatbox(bytes)
        uploaded?.let { uploadCache[cacheKey] = it }
        uploaded
    }

    private fun uploadToUguu(bytes: ByteArray): String? = try {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("files[]", "cover.png", bytes.toRequestBody("image/*".toMediaTypeOrNull()))
            .build()
        networkHelper.client.newCall(Request.Builder().url(UGUU_URI).post(body).build())
            .execute().use { response ->
                if (response.isSuccessful) response.body?.string()?.trim()?.takeIf { it.startsWith("http") } else null
            }
    } catch (e: Exception) {
        Log.e(TAG, "uguu.se upload failed: ${e.message}", e)
        null
    }

    private fun uploadToCatbox(bytes: ByteArray): String? = try {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("reqtype", "fileupload")
            .addFormDataPart("fileToUpload", "cover.png", bytes.toRequestBody("image/*".toMediaTypeOrNull()))
            .build()
        networkHelper.client.newCall(Request.Builder().url(CATBOX_URI).post(body).build())
            .execute().use { response ->
                if (response.isSuccessful) response.body?.string()?.trim()?.takeIf { it.startsWith("http") } else null
            }
    } catch (e: Exception) {
        Log.e(TAG, "catbox.moe upload failed: ${e.message}", e)
        null
    }
}
