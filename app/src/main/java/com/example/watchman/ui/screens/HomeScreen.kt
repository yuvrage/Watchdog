package com.example.watchman.ui.screens

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
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ShieldMoon
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.watchman.network.ApiService
import com.example.watchman.network.DeviceSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScanApk: () -> Unit,
    onScanDeviceApps: () -> Unit,
    onViewResult: () -> Unit,
    onViewBreakdown: () -> Unit,
    onShowAllApps: () -> Unit,
) {
    val backgroundGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
            Color.Transparent,
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "WATCHDOG") },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(36.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color.White.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ShieldMoon,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(padding),
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                HeroBanner()
                Spacer(modifier = Modifier.height(28.dp))

                DeviceSecuritySection(
                    onViewBreakdown = onViewBreakdown,
                    onShowAllApps = onShowAllApps,
                )

                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "Actions",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActionButton(
                        title = "Scan APK",
                        subtitle = "Upload a new build and get full-stack telemetry",
                        icon = Icons.Outlined.UploadFile,
                        onClick = onScanApk,
                    )
                    ActionButton(
                        title = "Scan Device Apps",
                        subtitle = "Scan installed apps from your device",
                        icon = Icons.Outlined.PhoneAndroid,
                        onClick = onScanDeviceApps,
                    )
                    ActionButton(
                        title = "View Result",
                        subtitle = "Track a task ID and monitor its status",
                        icon = Icons.Outlined.Analytics,
                        onClick = onViewResult,
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun HeroBanner() {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
        ),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "Android App Risk Scanner",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Scan installed or uploaded apps to detect leaked secrets, unsafe endpoints, trackers "
                        + "and dangerous permissions, then aggregate them into a device risk score.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.9f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun DeviceSecuritySection(
    onViewBreakdown: () -> Unit,
    onShowAllApps: () -> Unit,
) {
    val context = LocalContext.current
    var summary by remember { mutableStateOf<DeviceSummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val deviceId =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ANDROID_ID,
                ) ?: "unknown"
            val api = ApiService()
            summary = api.getDeviceSummary(deviceId)
        } catch (e: Exception) {
            error = e.message
        } finally {
            isLoading = false
        }
    }

    val rating = summary?.rating ?: 0.0
    val totals = summary?.totals

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                val progress = (rating / 10.0).coerceIn(0.0, 1.0).toFloat()
                CircularProgressIndicator(
                    progress = { progress },
                    strokeWidth = 10.dp,
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF22C55E),
                    trackColor = Color(0xFFE5E7EB),
                )
                Text(
                    text = if (isLoading) "…" else String.format("%.1f", rating),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Device security rating is",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = summary?.status ?: "SAFE",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    ),
                    modifier = Modifier
                        .background(Color(0xFF16A34A), shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Text(
                    text = error?.let { "Failed to load summary: $it" }
                        ?: "Explore which apps are creating high risk in your phone",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    ),
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(Color(0xFF111827)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = totals?.totalScanned?.toString() ?: "0",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Total apps scanned",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = "Show all apps →",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.clickable(onClick = onShowAllApps),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SecuritySummaryColumn(
                        label = "DANGEROUS",
                        value = totals?.dangerous?.toString() ?: "0",
                        description = "Dangerous apps",
                        color = Color(0xFFEF4444),
                    )
                    SecuritySummaryColumn(
                        label = "MODERATE",
                        value = totals?.moderate?.toString() ?: "0",
                        description = "Apps with mild issues",
                        color = Color(0xFFF97316),
                    )
                    SecuritySummaryColumn(
                        label = "SAFE",
                        value = totals?.safe?.toString() ?: "0",
                        description = "Safe apps",
                        color = Color(0xFF16A34A),
                    )
                }
            }
        }
    }
}

@Composable
private fun SecuritySummaryColumn(
    label: String,
    value: String,
    description: String,
    color: Color,
) {
    Column(
        modifier = Modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = color,
            ),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            ),
        )
    }
}

@Composable
private fun ActionButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val cardColor = MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(cardColor)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.7f),
                ),
            )
        }
    }
}


