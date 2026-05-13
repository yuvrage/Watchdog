package com.example.watchman.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.watchman.network.ApiService
import com.example.watchman.network.ScanResult
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultDetailScreen(
    taskId: String,
    onBack: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ScanResult?>(null) }

    LaunchedEffect(taskId) {
        val api = ApiService()
        val maxAttempts = 60 // More attempts for longer scans
        var attempt = 0
        var delayMs = 500L // Start with 500ms for faster initial polling

        while (attempt < maxAttempts && result == null && error == null) {
            attempt++
            try {
                val res = api.getScanResult(taskId)
                // If we got here, the result is ready
                result = res
                isLoading = false
                break
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                // If it's still processing, continue polling
                if (errorMsg.contains("still processing", ignoreCase = true) || errorMsg.contains("No result available", ignoreCase = true)) {
                    // Continue polling
                } else if (errorMsg.contains("failed", ignoreCase = true) || errorMsg.contains("FAILURE", ignoreCase = true)) {
                    // Task failed
                    error = "Scan failed: $errorMsg"
                    isLoading = false
                    break
                } else if (attempt >= 10) {
                    // After 10 attempts, show error if it's not a processing message
                    error = "Unable to load result: $errorMsg"
                    isLoading = false
                    break
                }
                // For other errors in early attempts, just retry
            }

            // Faster exponential backoff: 500ms -> 1s -> 2s -> 4s -> 8s (max)
            delay(delayMs)
            delayMs = (delayMs * 2).coerceAtMost(8000)
        }

        if (result == null && error == null) {
            isLoading = false
            error = "Scan is taking longer than expected. The scan may still be processing. Please check back later."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Scan Result") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Processing scan... this may take a moment")
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
                        text = "Failed to load result:",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error.orEmpty())
                }
            }

            result != null -> {
                ResultContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    scan = result!!,
                )
            }
        }
    }
}

@Composable
private fun ResultContent(
    modifier: Modifier = Modifier,
    scan: ScanResult,
) {
    LazyColumn(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            SummaryHeader(scan = scan)
        }
        item {
            SectionTitle("Risk Intelligence")
            RiskBreakdownGrid(scan = scan)
        }
        item {
            SectionTitle("High-Risk Permissions")
            TagSection(
                items = scan.permissions.filter {
                    it.contains("READ") ||
                        it.contains("WRITE") ||
                        it.contains("LOCATION") ||
                        it.contains("SMS") ||
                        it.contains("CALL")
                },
            )
        }
        item {
            SectionTitle("Trackers")
            TagSection(items = scan.trackers)
        }
        item {
            SectionTitle("Secrets Found")
            TagSection(items = scan.secrets)
        }
        item {
            SectionTitle("Sensitive Endpoints")
            TagSection(
                items = scan.endpoints.filter { e ->
                    e.contains("api") ||
                        e.contains("auth") ||
                        e.contains("login") ||
                        e.contains("user") ||
                        e.contains("token") ||
                        e.contains("admin") ||
                        e.startsWith("http")
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun SummaryHeader(scan: ScanResult) {
    val gradient = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
        ),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Column(
            modifier = Modifier
                .background(gradient)
                .padding(24.dp),
        ) {
            Text(
                text = scan.appName,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = scan.packageName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.7f),
                ),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SummaryRow(label = "Version", value = scan.versionName ?: "-")
                    SummaryRow(label = "Risk Level", value = scan.riskLevel)
                    SummaryRow(label = "Secrets", value = scan.secrets.size.toString())
                }
                RiskGauge(score = scan.riskScore)
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun RiskGauge(score: Double) {
    // Clamp score to 0-10 range to handle any old data
    val clampedScore = score.coerceIn(0.0, 10.0)
    val normalized = (clampedScore / 10.0).coerceIn(0.0, 1.0).toFloat()
    val color = scoreColor(clampedScore)

    Box(
        modifier = Modifier
            .height(120.dp)
            .fillMaxWidth(0.3f),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { normalized },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 10.dp,
            color = color,
            trackColor = Color.White.copy(alpha = 0.24f),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format("%.1f", clampedScore),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            )
            Text(
                text = when {
                    clampedScore >= 8.0 -> "Critical"
                    clampedScore >= 3.0 -> "Elevated"
                    else -> "Low"
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = color,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

private fun scoreColor(score: Double): Color =
    when {
        score >= 8.0 -> Color(0xFFFF6B6B) // DANGEROUS (only very high scores)
        score >= 3.0 -> Color(0xFFFFB347) // MODERATE
        else -> Color(0xFF4ADE80) // SAFE (most apps)
    }

@Composable
private fun RiskBreakdownGrid(scan: ScanResult) {
    val entries = listOf(
        "permissions_score",
        "tracker_score",
        "endpoint_score",
        "secret_score",
        "size_score",
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        entries.forEach { key ->
            val value = (scan.riskComponents[key] as? Number)?.toDouble() ?: 0.0
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = key.replaceFirstChar { it.uppercase() }.replace("_", " "),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (value / 100.0).coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (value > 70) {
                            Color(0xFFFF6B6B)
                        } else {
                            Color(0xFF34E3C9)
                        },
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${value.toInt()} / 100",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.7f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TagSection(items: List<String>) {
    if (items.isEmpty()) {
        Text(
            text = "No findings",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = 0.54f),
            ),
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BubbleChart,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.54f),
                    )
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}