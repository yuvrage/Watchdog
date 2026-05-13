package com.example.watchman.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchman.network.ApiService
import com.example.watchman.network.DeviceApp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import java.io.File

data class AppWithScanStatus(
    val packageName: String,
    val appName: String,
    val sourceDir: String?,
    val installerPackage: String?,
    val scanResult: DeviceApp? = null, // null if not scanned
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemAppsScreen(
    onBack: () -> Unit,
    onOpenResult: (String) -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var apps by remember { mutableStateOf<List<AppWithScanStatus>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val deviceId =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID,
                ) ?: "unknown"
            val api = ApiService()

            // Get installed apps
            val installed =
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter {
                        val isSystem = it.flags and ApplicationInfo.FLAG_SYSTEM != 0
                        val isUpdatedSystem = it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
                        !isSystem && !isUpdatedSystem
                    }
                    .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

            // Get scanned apps from backend
            val scannedApps = try {
                api.getDeviceApps(deviceId).associateBy { it.packageName }
            } catch (_: Exception) {
                emptyMap()
            }

            // Combine installed apps with scan status
            apps = installed.map {
                val pkgName = it.packageName
                val installerPkg = try {
                    pm.getInstallerPackageName(pkgName) ?: "Package installer"
                } catch (_: Exception) {
                    "Package installer"
                }
                AppWithScanStatus(
                    packageName = pkgName,
                    appName = pm.getApplicationLabel(it).toString(),
                    sourceDir = it.publicSourceDir ?: it.sourceDir,
                    installerPackage = installerPkg,
                    scanResult = scannedApps[pkgName],
                )
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apps") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            apps == null && error == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading apps…")
                }
            }

            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Unable to list apps.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error.orEmpty())
                }
            }

            else -> {
                val list = apps.orEmpty()
                val filteredList = if (searchQuery.isBlank()) {
                    list
                } else {
                    list.filter {
                        it.appName.contains(searchQuery, ignoreCase = true) ||
                            it.packageName.contains(searchQuery, ignoreCase = true)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search for applications") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {}),
                        shape = RoundedCornerShape(12.dp),
                    )

                    // Apps header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Apps (${filteredList.size}/${list.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }

                    // Scan All Apps button (prominent, full width)
                    var isScanningAll by remember { mutableStateOf(false) }
                    var scannedCount by remember { mutableStateOf(0) }
                    
                    ElevatedButton(
                        onClick = {
                            if (isScanningAll) return@ElevatedButton
                            scope.launch {
                                isScanningAll = true
                                scannedCount = 0
                                try {
                                    val deviceId =
                                        Settings.Secure.getString(
                                            context.contentResolver,
                                            Settings.Secure.ANDROID_ID,
                                        ) ?: "unknown"
                                    val api = ApiService()
                                    
                                    // Clear previous scans
                                    api.clearDeviceScans(deviceId)
                                    
                                    // Start scanning all apps
                                    var started = 0
                                    for (app in list) {
                                        val path = app.sourceDir ?: continue
                                        val file = File(path)
                                        if (!file.exists()) continue
                                        try {
                                            api.scanApk(file, deviceId) { _, _ -> }
                                            started++
                                            scannedCount = started
                                        } catch (_: Exception) {
                                            // continue with next app
                                        }
                                    }
                                    snackbarHostState.showSnackbar(
                                        "Cleared previous scans and started scanning $started apps",
                                    )
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(
                                        "Error: ${e.message}",
                                    )
                                } finally {
                                    isScanningAll = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        enabled = !isScanningAll && list.isNotEmpty(),
                    ) {
                        if (isScanningAll) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text("Scanning... $scannedCount/${list.size}")
                            }
                        } else {
                            Text("Scan All Apps")
                        }
                    }
                    
                    if (isScanningAll) {
                        Text(
                            text = "Scanning apps… $scannedCount of ${list.size} started",
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // App list
                    if (filteredList.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "No apps found",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 16.dp,
                                vertical = 8.dp,
                            ),
                        ) {
                            items(filteredList, key = { it.packageName }) { app ->
                                AppCard(
                                    app = app,
                                    onScan = {
                                        if (app.sourceDir.isNullOrEmpty()) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "APK path not accessible for ${app.appName}",
                                                )
                                            }
                                            return@AppCard
                                        }
                                        scope.launch {
                                            try {
                                                val file = File(app.sourceDir)
                                                if (!file.exists()) {
                                                    snackbarHostState.showSnackbar(
                                                        "APK file not found for ${app.appName}",
                                                    )
                                                    return@launch
                                                }
                                                val api = ApiService()
                                                val deviceId =
                                                    Settings.Secure.getString(
                                                        context.contentResolver,
                                                        Settings.Secure.ANDROID_ID,
                                                    ) ?: "unknown"
                                                val taskId =
                                                    api.scanApk(file, deviceId) { _, _ -> }
                                                onOpenResult(taskId)
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar(
                                                    "Scan failed: ${e.message}",
                                                )
                                            }
                                        }
                                    },
                                    pm = pm,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(
    app: AppWithScanStatus,
    onScan: () -> Unit,
    pm: PackageManager,
) {
    val isScanned = app.scanResult != null
    val riskLevel = app.scanResult?.riskLevel ?: "APP NOT SCANNED"
    // Clamp risk score to 0-10 range to handle any old data
    val riskScore = (app.scanResult?.riskScore ?: 0.0).coerceIn(0.0, 10.0)

    // Get installer name (simplified)
    val installerName = when {
        app.installerPackage?.contains("com.android.vending") == true -> "Google Play Store"
        app.installerPackage?.contains("samsung") == true -> "Galaxy Store"
        else -> app.installerPackage ?: "Package installer"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left side: Icon and app info
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) {
                // App icon
                val icon = remember(app.packageName) {
                    try {
                        pm.getApplicationIcon(app.packageName)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (icon != null) {
                    val bitmap = icon.toBitmap(64, 64)
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                Color.Gray.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = app.appName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // App details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = installerName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF16A34A),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Installed",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF16A34A),
                            ),
                        )
                    }
                }
            }

            // Right side: Risk badge and score
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Risk level badge
                val badgeColor = when (riskLevel.uppercase()) {
                    "SAFE", "LOW" -> Color(0xFF16A34A) // Green
                    "MODERATE" -> Color(0xFFF97316) // Orange
                    "HIGH", "DANGEROUS" -> Color(0xFFEF4444) // Red
                    else -> Color(0xFF3B82F6) // Blue for "APP NOT SCANNED"
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = riskLevel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )
                }

                // Risk score (only if scanned)
                if (isScanned) {
                    Text(
                        text = String.format("%.1f", riskScore),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                } else {
                    // SCAN NOW button for unscanned apps
                    ElevatedButton(
                        onClick = onScan,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(
                            text = "SCAN NOW",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}
