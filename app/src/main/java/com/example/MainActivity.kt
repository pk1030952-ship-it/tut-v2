package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.*
import com.example.ui.components.GlobalSearchModal
import com.example.ui.components.PinLockScreen
import com.example.ui.screens.*
import com.example.ui.theme.CoachMasterTheme
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.worker.WorkScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WorkScheduler.scheduleDailyAutomation(applicationContext)

        val openTabExtra = intent?.getStringExtra("open_tab")

        setContent {
            val vm: CoachMasterViewModel = viewModel()
            val config by vm.config.collectAsState()

            LaunchedEffect(openTabExtra) {
                if (openTabExtra == "ATTENDANCE") {
                    vm.selectTab(AppNavDestination.ATTENDANCE)
                }
            }

            CoachMasterTheme(darkTheme = config.darkMode) {
                CoachMasterApp(viewModel = vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachMasterApp(
    viewModel: CoachMasterViewModel = viewModel(),
    dashboardViewModel: DashboardViewModel = viewModel(),
    studentViewModel: StudentViewModel = viewModel(),
    attendanceViewModel: AttendanceViewModel = viewModel(),
    financeViewModel: FinanceViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val config by viewModel.config.collectAsState()
    val students by studentViewModel.students.collectAsState()
    val batches by studentViewModel.batches.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showMoreMenu by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearStatusMessage()
        }
    }

    if (!uiState.isUnlocked) {
        PinLockScreen(
            instituteName = config.instituteName,
            correctPin = config.adminPin,
            correctPassword = config.adminPassword,
            isBiometricAvailable = config.isBiometricEnabled,
            onUnlockSuccess = { viewModel.unlockAsAdmin() },
            onGuestLogin = { viewModel.loginAsGuest() }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = config.instituteName.ifBlank { "CoachMaster" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row {
                                Text(
                                    text = if (uiState.isAdmin) "Pankaj jii (Admin)" else "Guest Mode (Read-Only)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.isAdmin) IndigoPrimary else Color.Gray,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (uiState.isCloudConnected) " • Cloud Synced" else " • Local Mode",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.isCloudConnected) EmeraldSuccess else Color.Gray,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.openGlobalSearch() },
                            modifier = Modifier.testTag("top_nav_search")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Global Search", tint = MaterialTheme.colorScheme.onSurface)
                        }

                        IconButton(
                            onClick = { viewModel.toggleDarkMode() },
                            modifier = Modifier.testTag("top_nav_dark_mode")
                        ) {
                            Icon(
                                imageVector = if (config.darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Dark Mode",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { viewModel.selectTab(AppNavDestination.BATCHES) },
                            modifier = Modifier.testTag("top_nav_batches")
                        ) {
                            Icon(Icons.Default.Class, contentDescription = "Batches", tint = MaterialTheme.colorScheme.onSurface)
                        }

                        IconButton(
                            onClick = { viewModel.selectTab(AppNavDestination.EXAMS) },
                            modifier = Modifier.testTag("top_nav_exams")
                        ) {
                            Icon(Icons.Default.Assignment, contentDescription = "Exams", tint = MaterialTheme.colorScheme.onSurface)
                        }

                        IconButton(
                            onClick = { viewModel.selectTab(AppNavDestination.EXPENSES) },
                            modifier = Modifier.testTag("top_nav_expenses")
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = "Expenses", tint = MaterialTheme.colorScheme.onSurface)
                        }

                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.testTag("top_more_menu")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Staff & Teachers") },
                                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                                    onClick = {
                                        viewModel.selectTab(AppNavDestination.STAFF)
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Weekly Timetable") },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                                    onClick = {
                                        viewModel.selectTab(AppNavDestination.TIMETABLE)
                                        showMoreMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reports & Analytics") },
                                    leadingIcon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                                    onClick = {
                                        viewModel.selectTab(AppNavDestination.REPORTS)
                                        showMoreMenu = false
                                    }
                                )
                                if (uiState.isAdmin) {
                                    DropdownMenuItem(
                                        text = { Text("System Settings") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = {
                                            viewModel.selectTab(AppNavDestination.SETTINGS)
                                            showMoreMenu = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Logout / Lock") },
                                    leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null) },
                                    onClick = {
                                        viewModel.lockApp()
                                        showMoreMenu = false
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    windowInsets = NavigationBarDefaults.windowInsets
                ) {
                    val primaryTabs = listOf(
                        Triple(AppNavDestination.DASHBOARD, Icons.Default.Dashboard, "Dashboard"),
                        Triple(AppNavDestination.STUDENTS, Icons.Default.People, "Students"),
                        Triple(AppNavDestination.BATCHES, Icons.Default.Layers, "Batches"),
                        Triple(AppNavDestination.ATTENDANCE, Icons.Default.FactCheck, "Attendance"),
                        Triple(AppNavDestination.FINANCE, Icons.Default.Payments, "Fees")
                    )

                    primaryTabs.forEach { (tab, icon, label) ->
                        val isSelected = uiState.currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(tab) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = IndigoPrimary,
                                selectedTextColor = IndigoPrimary,
                                indicatorColor = IndigoPrimary.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier.testTag("bottom_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (uiState.currentTab) {
                    AppNavDestination.DASHBOARD -> DashboardScreen(
                        dashboardViewModel = dashboardViewModel,
                        isAdmin = uiState.isAdmin,
                        onSelectTab = { viewModel.selectTab(it) },
                        onNavigateToAttendanceForDate = { date ->
                            attendanceViewModel.setSelectedDate(date)
                            viewModel.navigateToAttendanceForDate(date)
                        },
                        onSelectStudentForPayment = { student ->
                            financeViewModel.selectStudentForPayment(student)
                            viewModel.selectStudentForPayment(student)
                        }
                    )
                    AppNavDestination.STUDENTS -> StudentsScreen(
                        studentViewModel = studentViewModel,
                        isAdmin = uiState.isAdmin,
                        onSelectStudentForPayment = { student ->
                            financeViewModel.selectStudentForPayment(student)
                            viewModel.selectStudentForPayment(student)
                        }
                    )
                    AppNavDestination.BATCHES -> BatchesScreen(viewModel = viewModel)
                    AppNavDestination.ATTENDANCE -> AttendanceScreen(
                        attendanceViewModel = attendanceViewModel,
                        isAdmin = uiState.isAdmin
                    )
                    AppNavDestination.FINANCE -> FinanceScreen(
                        financeViewModel = financeViewModel,
                        isAdmin = uiState.isAdmin
                    )
                    AppNavDestination.EXAMS -> ExamsScreen(viewModel = viewModel)
                    AppNavDestination.EXPENSES -> ExpensesScreen(viewModel = viewModel)
                    AppNavDestination.STAFF -> StaffScreen(viewModel = viewModel)
                    AppNavDestination.TIMETABLE -> TimetableScreen(viewModel = viewModel)
                    AppNavDestination.REPORTS -> ReportsScreen(viewModel = viewModel)
                    AppNavDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }

        if (uiState.showGlobalSearchModal) {
            GlobalSearchModal(
                students = students,
                batches = batches,
                isAdmin = uiState.isAdmin,
                onDismiss = { viewModel.closeGlobalSearch() },
                onSelectStudent = { student ->
                    viewModel.closeGlobalSearch()
                    financeViewModel.selectStudentForPayment(student)
                    viewModel.selectStudentForPayment(student)
                },
                onSelectBatchesTab = {
                    viewModel.closeGlobalSearch()
                    viewModel.selectTab(AppNavDestination.BATCHES)
                },
                onQuickAdd = { destination ->
                    viewModel.closeGlobalSearch()
                    viewModel.selectTab(destination)
                }
            )
        }
    }
}
