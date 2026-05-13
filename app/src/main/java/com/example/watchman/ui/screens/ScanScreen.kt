package com.example.watchman.ui.screens

import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.watchman.network.ApiService
import com.example.watchman.network.copyUriToTempFile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onNavigateToResult: (taskId: String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                selectedUri = uri
                selectedFileName = null // reset until we actually copy
                status = ""
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Upload & Scan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.CloudUpload,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
        ) {
            Text(
                text = "APK Security Analysis",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Upload your APK file to perform comprehensive security scanning",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                ),
            )
            Spacer(modifier = Modifier.height(32.dp))

            UploadCard(
                fileName = selectedFileName,
                hasSelection = selectedUri != null,
                onClick = {
                    filePickerLauncher.launch(
                        arrayOf("application/vnd.android.package-archive", "application/octet-stream"),
                    )
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            ElevatedButton(
                onClick = {
                    val uri = selectedUri ?: return@ElevatedButton
                    scope.launch {
                        isUploading = true
                        status = "Uploading..."
                        progress = 0f
                        errorMessage = null

                        try {
                            val contentResolver = context.contentResolver
                            val name = runCatching {
                                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (cursor.moveToFirst() && nameIndex >= 0) {
                                        cursor.getString(nameIndex)
                                    } else {
                                        null
                                    }
                                }
                            }.getOrNull()

                            val tempFile =
                                copyUriToTempFile(
                                    context = context,
                                    uri = uri,
                                    fileNameHint = name ?: "upload.apk",
                                )

                            selectedFileName = tempFile.name

                            val api = ApiService()
                            val deviceId =
                                Settings.Secure.getString(
                                    context.contentResolver,
                                    Settings.Secure.ANDROID_ID,
                                ) ?: "unknown"
                            val taskId =
                                api.scanApk(tempFile, deviceId) { sent, total ->
                                    progress = if (total > 0) sent.toFloat() / total.toFloat() else 0f
                                }

                            isUploading = false
                            progress = 1f
                            status = "Upload complete"

                            onNavigateToResult(taskId)
                        } catch (e: Exception) {
                            isUploading = false
                            status = ""
                            progress = 0f
                            errorMessage = "Error: ${e.message}"
                        }
                    }
                },
                enabled = selectedUri != null && !isUploading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudUpload,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isUploading) "Uploading..." else "Upload & Scan")
            }

            if (progress > 0f && progress < 1f) {
                Spacer(modifier = Modifier.height(20.dp))
                Column {
                    RowBetween(
                        left = {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                ),
                            )
                        },
                        right = {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ErrorCard(message = errorMessage!!)
            }
        }
    }
}

@Composable
private fun UploadCard(
    fileName: String?,
    hasSelection: Boolean,
    onClick: () -> Unit,
) {
    val borderColor =
        if (hasSelection) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)

    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.UploadFile,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasSelection) "File selected" else "Tap to select APK file",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (fileName != null) {
                    fileName
                } else {
                    "Supported format: .apk"
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {
            TextButton(onClick = {}) {
                Text("Close")
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Outlined.CloudUpload,
                contentDescription = null,
            )
        },
        title = {
            Text(text = "Upload error")
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
            )
        },
    )
}

@Composable
private fun RowBetween(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        left()
        right()
    }
}


