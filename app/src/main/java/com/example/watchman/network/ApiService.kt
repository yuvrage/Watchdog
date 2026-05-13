package com.example.watchman.network

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import okio.buffer
import okio.sink

private interface RetrofitApi {
    @Multipart
    @POST("/scan/apk")
    suspend fun scanApk(
        @Part apk: MultipartBody.Part,
        @Part("device_id") deviceId: RequestBody,
    ): ScanResultEnvelope

    @GET("/scan/result/{taskId}")
    suspend fun getScanResult(
        @Path("taskId") taskId: String,
    ): ScanResultEnvelope

    @GET("/device/summary/{deviceId}")
    suspend fun getDeviceSummary(
        @Path("deviceId") deviceId: String,
    ): DeviceSummary

    @GET("/device/apps/{deviceId}")
    suspend fun getDeviceApps(
        @Path("deviceId") deviceId: String,
    ): List<DeviceApp>

    @DELETE("/device/clear/{deviceId}")
    suspend fun clearDeviceScans(
        @Path("deviceId") deviceId: String,
    ): Map<String, String>
}

object ApiConfig {
    // FastAPI backend (STANDARD) for watchdog
    const val BASE_URL: String = "http://10.167.130.36:8000"
}

class ApiService(
    baseUrl: String = ApiConfig.BASE_URL,
) {
    private val api: RetrofitApi

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS) // Faster connection
            .readTimeout(3, TimeUnit.MINUTES)
            .writeTimeout(3, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true) // Retry on failure
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(RetrofitApi::class.java)
    }

    /**
     * Upload an APK file for scanning with real-time progress tracking.
     */
    suspend fun scanApk(
        file: File,
        deviceId: String,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): String {
        val fileSize = file.length()
        onProgress(0, fileSize)

        // Create a RequestBody with progress tracking
        val requestBody = object : RequestBody() {
            private val mediaType = "application/vnd.android.package-archive".toMediaTypeOrNull()
            
            override fun contentType() = mediaType
            
            override fun contentLength() = fileSize
            
            override fun writeTo(sink: okio.BufferedSink) {
                var uploaded = 0L
                var lastProgressUpdate = 0L
                val progressUpdateInterval = maxOf(fileSize / 50, 1024 * 1024) // Update every 2% or 1MB, whichever is larger
                
                // sink is already a BufferedSink, use it directly
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(64 * 1024) // 64KB buffer for maximum performance
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        sink.write(buffer, 0, bytesRead)
                        uploaded += bytesRead
                        // Throttle progress updates to avoid performance issues
                        if (uploaded - lastProgressUpdate >= progressUpdateInterval || uploaded == fileSize) {
                            onProgress(uploaded, fileSize)
                            lastProgressUpdate = uploaded
                        }
                    }
                    sink.flush()
                }
            }
        }

        val part = MultipartBody.Part.createFormData(
            name = "apk",
            filename = file.name,
            body = requestBody,
        )

        val deviceIdBody =
            deviceId.toRequestBody("text/plain".toMediaType())

        val envelope = api.scanApk(part, deviceIdBody)

        onProgress(fileSize, fileSize)

        return envelope.taskId
            ?: throw IllegalStateException("No task_id returned from backend")
    }

    /**
     * Poll for scan result, mapping the raw envelope into the UI-friendly `ScanResult`.
     */
    suspend fun getScanResult(taskId: String): ScanResult {
        val envelope = api.getScanResult(taskId)

        // If the result is null, it's still processing or in an intermediate state.
        if (envelope.result == null) {
            // Check for explicit error states, otherwise assume it's still processing.
            if (envelope.status == "FAILURE" || envelope.error != null) {
                throw IllegalStateException(envelope.error ?: "Scan failed")
            }
            // Task is not finished yet, tell the caller to poll again.
            throw IllegalStateException("Scan still processing")
        }

        val apk = envelope.result.apk
            ?: throw IllegalStateException("Missing apk node in result")

        val secretsNode = envelope.result.secrets

        // Filter out invalid secrets (binary data, system paths, etc.)
        val mergedSecrets = buildList {
            secretsNode?.firebase?.let { 
                addAll(it.filter { secret -> isValidSecret(secret) }) 
            }
            secretsNode?.aws?.let { 
                addAll(it.filter { secret -> isValidSecret(secret) }) 
            }
            secretsNode?.tokens?.let { 
                addAll(it.filter { secret -> isValidSecret(secret) }) 
            }
            secretsNode?.jwt?.let { 
                addAll(it.filter { secret -> isValidSecret(secret) }) 
            }
        }

        @Suppress("UNCHECKED_CAST")
        val riskComponents = (apk.riskComponents ?: emptyMap()) as Map<String, Any>

        return ScanResult(
            appName = apk.appName ?: "Unknown",
            packageName = apk.packageName ?: "-",
            versionName = apk.versionName,
            riskScore = apk.riskScore ?: 0.0,
            riskLevel = apk.riskLevel ?: "UNKNOWN",
            riskComponents = riskComponents,
            permissions = apk.permissions.orEmpty(),
            trackers = apk.trackers.orEmpty(),
            endpoints = apk.endpoints.orEmpty(),
            secrets = mergedSecrets,
        )
    }

    suspend fun getDeviceSummary(deviceId: String): DeviceSummary =
        api.getDeviceSummary(deviceId)

    suspend fun getDeviceApps(deviceId: String): List<DeviceApp> =
        api.getDeviceApps(deviceId)

    suspend fun clearDeviceScans(deviceId: String): Map<String, String> =
        api.clearDeviceScans(deviceId)
}

/**
 * Copy arbitrary content Uri to a temporary file that can be uploaded by OkHttp.
 */
suspend fun copyUriToTempFile(
    context: Context,
    uri: android.net.Uri,
    fileNameHint: String = "upload.apk",
): File = withContext(Dispatchers.IO) {
    val fileName = if (fileNameHint.endsWith(".apk")) fileNameHint else "$fileNameHint.apk"
    val tempFile = File.createTempFile(fileName.removeSuffix(".apk"), ".apk", context.cacheDir)

    val resolver = context.contentResolver
    resolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output ->
            copyStream(input, output)
        }
    } ?: throw IllegalStateException("Unable to open input stream for URI: $uri")

    tempFile
}

private fun copyStream(input: InputStream, output: FileOutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read <= 0) break
        output.write(buffer, 0, read)
    }
}

/**
 * Filter out invalid secrets that are likely binary data, system paths, or false positives.
 */
private fun isValidSecret(secret: String): Boolean {
    if (secret.isBlank()) return false
    
    // Filter out common false positives
    val invalidPatterns = listOf(
        "system",
        "image",
        "android",
        "res/",
        "assets/",
        "lib/",
        "classes",
        ".dex",
        ".so",
        ".xml",
        ".png",
        ".jpg",
        ".jpeg",
        ".gif",
        ".webp",
        "META-INF",
        "AndroidManifest",
    )
    
    val lowerSecret = secret.lowercase()
    if (invalidPatterns.any { lowerSecret.contains(it) }) {
        return false
    }
    
    // Filter out very long strings (likely binary data)
    if (secret.length > 500) {
        return false
    }
    
    // Filter out strings with too many non-printable characters
    val nonPrintableCount = secret.count { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }
    if (nonPrintableCount > secret.length * 0.1) {
        return false
    }
    
    // Must match secret patterns (Firebase, AWS, JWT, etc.)
    val secretPatterns = listOf(
        Regex("AIza[0-9A-Za-z\\-_]{35}"), // Firebase
        Regex("AKIA[0-9A-Z]{16}"), // AWS
        Regex("Bearer\\s+[A-Za-z0-9\\.\\-_]+"), // Bearer token
        Regex("[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+"), // JWT
        Regex("sk_live_[0-9a-zA-Z]{24,99}"), // Stripe
    )
    
    return secretPatterns.any { it.matches(secret) }
}


