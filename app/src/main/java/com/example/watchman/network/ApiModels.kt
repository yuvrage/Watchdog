package com.example.watchman.network

import com.google.gson.annotations.SerializedName

/**
 * Raw response coming from the backend, shaped like:
 *
 * {
 *   "status": "PENDING|STARTED|SUCCESS|FAILURE",
 *   "task_id": "...",
 *   "result": {
 *     "apk": { ... },
 *     "secrets": { ... }
 *   } or null,
 *   "error": null or "message"
 * }
 */
data class ScanResultEnvelope(
    @SerializedName("status") val status: String? = null,
    @SerializedName("task_id") val taskId: String? = null,
    @SerializedName("result") val result: ScanResultResult? = null,
    @SerializedName("error") val error: String? = null,
)

data class ScanResultResult(
    @SerializedName("apk") val apk: ApkInfo? = null,
    @SerializedName("secrets") val secrets: SecretsInfo? = null,
)

data class ApkInfo(
    @SerializedName("app_name") val appName: String? = null,
    @SerializedName("package") val packageName: String? = null,
    @SerializedName("version_name") val versionName: String? = null,
    @SerializedName("risk_score") val riskScore: Double? = null,
    @SerializedName("risk_level") val riskLevel: String? = null,
    @SerializedName("risk_components") val riskComponents: Map<String, Any>? = null,
    @SerializedName("permissions") val permissions: List<String>? = null,
    @SerializedName("trackers") val trackers: List<String>? = null,
    @SerializedName("endpoints") val endpoints: List<String>? = null,
)

data class SecretsInfo(
    @SerializedName("firebase") val firebase: List<String>? = null,
    @SerializedName("aws") val aws: List<String>? = null,
    @SerializedName("tokens") val tokens: List<String>? = null,
    @SerializedName("jwt") val jwt: List<String>? = null,
)

/**
 * UI-facing scan result model, intentionally mirroring the Flutter `ScanResult` class.
 */
data class ScanResult(
    val appName: String,
    val packageName: String,
    val versionName: String?,
    val riskScore: Double,
    val riskLevel: String,
    val riskComponents: Map<String, Any>,
    val permissions: List<String>,
    val trackers: List<String>,
    val endpoints: List<String>,
    val secrets: List<String>,
)

data class DeviceTotals(
    @SerializedName("total_scanned") val totalScanned: Int,
    @SerializedName("dangerous") val dangerous: Int,
    @SerializedName("moderate") val moderate: Int,
    @SerializedName("safe") val safe: Int,
)

data class DeviceSummary(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("rating") val rating: Double,
    @SerializedName("status") val status: String,
    @SerializedName("totals") val totals: DeviceTotals,
)

data class DeviceApp(
    @SerializedName("package_name") val packageName: String,
    @SerializedName("app_name") val appName: String?,
    @SerializedName("risk_score") val riskScore: Double,
    @SerializedName("risk_level") val riskLevel: String,
)


