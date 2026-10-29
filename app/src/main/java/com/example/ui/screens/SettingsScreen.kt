package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CoachMasterViewModel
import com.example.ui.theme.CoralWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary

@Composable
fun SettingsScreen(
    viewModel: CoachMasterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val config by viewModel.config.collectAsState()

    var instName by remember(config.instituteName) { mutableStateOf(config.instituteName) }
    var instAddress by remember(config.address) { mutableStateOf(config.address) }
    var instPhone by remember(config.contactPhone) { mutableStateOf(config.contactPhone) }

    var adminPin by remember(config.adminPin) { mutableStateOf(config.adminPin) }
    var adminPassword by remember(config.adminPassword) { mutableStateOf(config.adminPassword) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showDemoConfirm by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }

    // File Picker for Restore from Backup JSON
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { content ->
                pendingRestoreJson = content
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "System Preferences",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Manage institute profile, cloud data, and application settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // 1. Institute Profile Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apartment, contentDescription = null, tint = IndigoPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Institute Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = instName,
                        onValueChange = { instName = it },
                        label = { Text("Institute Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setting_inst_name")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = instAddress,
                        onValueChange = { instAddress = it },
                        label = { Text("Contact / Address (For Receipts)") },
                        modifier = Modifier.fillMaxWidth().testTag("setting_inst_address")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = instPhone,
                        onValueChange = { instPhone = it },
                        label = { Text("Contact Phone") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setting_inst_phone")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            viewModel.updateConfig(
                                config.copy(
                                    instituteName = instName.trim(),
                                    address = instAddress.trim(),
                                    contactPhone = instPhone.trim()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("save_branding_btn")
                    ) {
                        Text("Save Profile")
                    }
                }
            }
        }

        // 2. Data Portability (Export Backup JSON & Restore from Backup JSON)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = EmeraldSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Data Portability & Cloud Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connected to Firebase Project: tution-pro-32a33 (tuitionAppDb/mainData). Export a local JSON backup or restore data to the Cloud database.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.exportBackupJson(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("export_backup_btn")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download / Share Backup JSON")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { importLauncher.launch("*/*") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("import_backup_btn")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore from Backup JSON")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.triggerCloudSync(pullFirst = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("setting_cloud_pull_btn")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pull from Firebase", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.triggerCloudSync(pullFirst = false) },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("setting_cloud_sync_btn")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Push to Firebase", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3. Security & PIN / Password Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = IndigoPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Admin Security (Password & PIN)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = adminPassword,
                            onValueChange = { adminPassword = it },
                            label = { Text("Admin Password") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = adminPin,
                            onValueChange = { if (it.length <= 4) adminPin = it },
                            label = { Text("4-Digit PIN") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("setting_admin_pin")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (adminPin.length == 4 && adminPassword.isNotBlank()) {
                                viewModel.updateConfig(
                                    config.copy(
                                        adminPin = adminPin,
                                        adminPassword = adminPassword.trim()
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Update Security Credentials")
                    }
                }
            }
        }

        // 4. Demo Data & Danger Zone
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Demo Data & Danger Zone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showDemoConfirm = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("load_demo_data_btn")
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Load Demo Data")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralWarning),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("factory_reset_btn")
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp), tint = CoralWarning)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Factory Reset Database", color = CoralWarning)
                    }
                }
            }
        }
    }

    if (pendingRestoreJson != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreJson = null },
            title = { Text("Overwrite Cloud Database?") },
            text = { Text("Restoring this backup will overwrite current data in both the app and Firebase Cloud. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingRestoreJson?.let { viewModel.importBackupJson(it) }
                        pendingRestoreJson = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralWarning)
                ) {
                    Text("Confirm Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreJson = null }) { Text("Cancel") }
            }
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("CRITICAL: Factory Reset") },
            text = { Text("Permanently delete ALL data from the app and Firebase Cloud Database? This CANNOT be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.factoryReset()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralWarning)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showDemoConfirm) {
        AlertDialog(
            onDismissRequest = { showDemoConfirm = false },
            title = { Text("Load Demo Data") },
            text = { Text("This will add sample students, batches, and fees. Proceed?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.seedDemoData()
                        showDemoConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDemoConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
