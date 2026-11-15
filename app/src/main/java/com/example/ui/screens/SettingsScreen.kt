package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CoachMasterViewModel
import com.example.ui.theme.CoralWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.util.SecurityUtils

@Composable
fun SettingsScreen(
    viewModel: CoachMasterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.config.collectAsState()

    var instName by remember(config.instituteName) { mutableStateOf(config.instituteName) }
    var instAddress by remember(config.address) { mutableStateOf(config.address) }
    var instPhone by remember(config.contactPhone) { mutableStateOf(config.contactPhone) }
    var defaultClassesStr by remember(config.defaultClassesPerCycle) {
        mutableStateOf(config.defaultClassesPerCycle.toString())
    }

    var newAdminPin by remember { mutableStateOf("") }
    var newAdminPassword by remember { mutableStateOf("") }
    var securityError by remember { mutableStateOf<String?>(null) }

    var showResetConfirm by remember { mutableStateOf(false) }
    var showDemoConfirm by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }

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
                text = "Manage institute profile, fee cycle rules, cloud collections, and hashed security credentials.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // 1. Institute Profile & Fee Cycle Rules Card
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
                            text = "Institute Profile & Fee Cycle Rules",
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
                        onValueChange = { instPhone = it.filter { ch -> ch.isDigit() }.take(10) },
                        label = { Text("Contact Phone (10 Digits)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("setting_inst_phone")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = defaultClassesStr,
                        onValueChange = { defaultClassesStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Default Classes per Fee Cycle (e.g. 30 or 26)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("setting_default_classes_cycle")
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = defaultClassesStr == "30",
                            onClick = { defaultClassesStr = "30" },
                            label = { Text("30 Classes (Default)") }
                        )
                        FilterChip(
                            selected = defaultClassesStr == "26",
                            onClick = { defaultClassesStr = "26" },
                            label = { Text("26 Classes (Excl. Sundays)") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val parsedCycle = defaultClassesStr.toIntOrNull()?.takeIf { it > 0 } ?: 30
                            viewModel.updateConfig(
                                config.copy(
                                    instituteName = instName.trim(),
                                    address = instAddress.trim(),
                                    contactPhone = instPhone.trim(),
                                    defaultClassesPerCycle = parsedCycle
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("save_branding_btn")
                    ) {
                        Text("Save Profile & Billing Rules")
                    }
                }
            }
        }

        // 2. Data Portability & Firestore Collections Architecture
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
                            text = "Data Portability & Cloud Collections",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Firestore Collections Architecture Active: students/{studentId}, attendance/{date}, payments/{paymentId}. Unlimited scalability with no 1MB limit.",
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

                    Button(
                        onClick = { viewModel.runEncryptedBackupNow(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("encrypted_backup_now_btn")
                    ) {
                        Icon(Icons.Default.EnhancedEncryption, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run & Share Encrypted Auto-Backup (AES-256)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { importLauncher.launch("*/*") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("import_backup_btn")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore from Backup (Plain or Encrypted JSON)")
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

        // 2B. WorkManager Daily Automation Card (9:00 AM Roll-Call + Nightly Encrypted Backup)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = IndigoPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WorkManager Daily Automation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• 9:00 AM Daily Roll-Call Reminder: \"Aaj ki tuition class start ho gayi hai! Roll Call lein.\"\n• 11:00 PM Nightly Encrypted JSON Auto-Backup (AES-256-GCM).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { viewModel.triggerTestRollCallNotification(context) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("test_roll_call_notification_btn")
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Morning Roll-Call Notification Now")
                    }
                }
            }
        }

        // 3. Security & Hashed PIN / Password + Biometric Unlock Card
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
                            text = "Admin Security & Biometric Unlock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your Admin PIN & Password are encrypted with salted SHA-256 hashing in Room DB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Hardware Biometric Unlock", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = "Use Android Fingerprint / Face Unlock on the Lock Screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = config.isBiometricEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.updateConfig(config.copy(isBiometricEnabled = enabled))
                            },
                            modifier = Modifier.testTag("setting_biometric_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newAdminPassword,
                            onValueChange = {
                                newAdminPassword = it
                                securityError = null
                            },
                            label = { Text("New Admin Password") },
                            placeholder = { Text("Leave blank to keep") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f).testTag("setting_admin_password")
                        )

                        OutlinedTextField(
                            value = newAdminPin,
                            onValueChange = {
                                newAdminPin = it.filter { ch -> ch.isDigit() }.take(4)
                                securityError = null
                            },
                            label = { Text("New 4-Digit PIN") },
                            placeholder = { Text("4 digits") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f).testTag("setting_admin_pin")
                        )
                    }

                    if (securityError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = securityError!!,
                            color = CoralWarning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (newAdminPin.isBlank() && newAdminPassword.isBlank()) {
                                securityError = "Enter a new 4-digit PIN or a new Admin Password to update."
                                return@Button
                            }
                            if (newAdminPin.isNotBlank() && (newAdminPin.length != 4 || !newAdminPin.all { it.isDigit() })) {
                                securityError = "PIN must be exactly 4 numeric digits."
                                return@Button
                            }
                            if (newAdminPassword.isNotBlank() && newAdminPassword.trim().length < 4) {
                                securityError = "Password must be at least 4 characters."
                                return@Button
                            }
                            val updatedPinHash = if (newAdminPin.isNotBlank()) {
                                SecurityUtils.hashSecret(newAdminPin)
                            } else {
                                config.adminPin
                            }
                            val updatedPasswordHash = if (newAdminPassword.isNotBlank()) {
                                SecurityUtils.hashSecret(newAdminPassword.trim())
                            } else {
                                config.adminPassword
                            }
                            viewModel.updateConfig(
                                config.copy(
                                    adminPin = updatedPinHash,
                                    adminPassword = updatedPasswordHash
                                )
                            )
                            newAdminPin = ""
                            newAdminPassword = ""
                            securityError = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("update_security_btn")
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
            text = { Text("Restoring this backup will overwrite current data in both the app and Firebase Cloud Collections. Continue?") },
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
            text = { Text("Permanently delete ALL data from the app and Firebase Cloud Collections? This CANNOT be undone.") },
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
