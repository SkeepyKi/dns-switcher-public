package com.example.dns_switcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.dns_switcher.ui.components.*
import com.example.dns_switcher.ui.screens.MainScreen
import com.example.dns_switcher.ui.screens.SettingsScreen
import com.example.dns_switcher.ui.theme.AppColors
import com.example.dns_switcher.ui.theme.DNSswitcherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

enum class Screen { MAIN, SETTINGS }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DNSswitcherTheme {
                DnsSwitcherApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSwitcherApp() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    var currentScreen by remember { mutableStateOf(Screen.MAIN) }
    val coroutineScope = rememberCoroutineScope()

    var dnsServer by remember {
        mutableStateOf(prefs.getString(Constants.KEY_DNS_SERVER, Constants.DEFAULT_DNS) ?: Constants.DEFAULT_DNS)
    }
    var testDomain by remember {
        mutableStateOf(prefs.getString("test_domain", "gemini.google.com") ?: "gemini.google.com")
    }
    var selectedPackages by remember {
        mutableStateOf(
            prefs.getStringSet(Constants.KEY_TARGET_PACKAGES, Constants.DEFAULT_PACKAGES)
                ?: Constants.DEFAULT_PACKAGES
        )
    }
    var autoStartEnabled by remember {
        mutableStateOf(prefs.getBoolean(Constants.KEY_AUTO_START, false))
    }
    var isServiceRunning by remember { mutableStateOf(false) }
    var currentApp by remember { mutableStateOf("—") }
    var dnsState by remember { mutableStateOf("—") }

    // Проверка разрешений
    var hasUsageAccess by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var hasSecureSettings by remember { mutableStateOf(checkWriteSecureSettings(context)) }
    var hasBatteryOptimization by remember { mutableStateOf(checkBatteryOptimization(context)) }
    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission(context)) }

    // Диалоги
    var showAppPicker by remember { mutableStateOf(false) }
    var showSetupGuide by remember { mutableStateOf(false) }

    val pingResults = remember { mutableStateMapOf<String, String>() }
    var isTestingPings by remember { mutableStateOf(false) }

    fun runPingTest() {
        if (isTestingPings) return
        if (dnsServer.isBlank()) return

        isTestingPings = true
        coroutineScope.launch(Dispatchers.IO) {
            val serverToTest = dnsServer.trim()
            val resolvedIp = try {
                InetAddress.getByName(serverToTest).hostAddress ?: ""
            } catch (e: Exception) {
                ""
            }

            if (resolvedIp.isEmpty()) {
                withContext(Dispatchers.Main) {
                    pingResults[serverToTest] = "недоступен"
                    isTestingPings = false
                }
                return@launch
            }

            val ping = DnsPingTester.testDnsPing(serverToTest, testDomain.trim(), timeoutMs = 1500)
            val resultText = if (ping >= 0) "$ping ms" else "таймаут"

            withContext(Dispatchers.Main) {
                pingResults[serverToTest] = resultText
                isTestingPings = false
            }
        }
    }

    // Загружаем список приложений и запускаем первый пинг-тест
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        installedApps = loadInstalledApps(context)
        runPingTest()
    }

    // Приём broadcast от DnsMonitorService
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                currentApp = intent.getStringExtra(DnsMonitorService.EXTRA_CURRENT_APP) ?: "—"
                dnsState = intent.getStringExtra(DnsMonitorService.EXTRA_DNS_STATE) ?: "—"
                isServiceRunning = intent.getBooleanExtra(DnsMonitorService.EXTRA_IS_RUNNING, false)
            }
        }
        val filter = IntentFilter(DnsMonitorService.ACTION_STATUS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Визард настройки разрешений
    if (showSetupGuide) {
        SetupWizardDialog(
            onDismiss = {
                showSetupGuide = false
                hasUsageAccess = checkUsageStatsPermission(context)
                hasSecureSettings = checkWriteSecureSettings(context)
                hasBatteryOptimization = checkBatteryOptimization(context)
                hasNotificationPermission = checkNotificationPermission(context)
            }
        )
    }

    // Диалог выбора приложений
    if (showAppPicker) {
        AppPickerDialog(
            installedApps = installedApps,
            selectedPackages = selectedPackages,
            onSelectionChanged = { newSelection ->
                selectedPackages = newSelection
                prefs.edit().putStringSet(Constants.KEY_TARGET_PACKAGES, newSelection).apply()
            },
            onDismiss = { showAppPicker = false }
        )
    }

    Scaffold(
        containerColor = AppColors.DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentScreen == Screen.MAIN) "Главная" else "Настройки",
                        color = AppColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.DarkBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = AppColors.CardBackground,
                contentColor = AppColors.TextPrimary
            ) {
                NavigationBarItem(
                    selected = currentScreen == Screen.MAIN,
                    onClick = { currentScreen = Screen.MAIN },
                    icon = { Text("🏠", fontSize = 20.sp) },
                    label = { Text("Главная") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppColors.AccentCyan,
                        selectedTextColor = AppColors.AccentCyan,
                        indicatorColor = AppColors.AccentCyan.copy(alpha = 0.2f),
                        unselectedIconColor = AppColors.TextSecondary,
                        unselectedTextColor = AppColors.TextSecondary
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.SETTINGS,
                    onClick = { currentScreen = Screen.SETTINGS },
                    icon = { Text("⚙️", fontSize = 20.sp) },
                    label = { Text("Настройки") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppColors.AccentCyan,
                        selectedTextColor = AppColors.AccentCyan,
                        indicatorColor = AppColors.AccentCyan.copy(alpha = 0.2f),
                        unselectedIconColor = AppColors.TextSecondary,
                        unselectedTextColor = AppColors.TextSecondary
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
                Crossfade(
                    targetState = currentScreen,
                    animationSpec = tween(durationMillis = 200),
                    label = "screen_transition"
                ) { targetScreen ->
                    when (targetScreen) {
                        Screen.MAIN -> {
                        MainScreen(
                            isRunning = isServiceRunning,
                            currentApp = currentApp,
                            dnsState = dnsState,
                            excludedCount = selectedPackages.size,
                            canStart = hasUsageAccess && hasSecureSettings,
                            onToggleService = {
                                if (isServiceRunning) {
                                    context.stopService(Intent(context, DnsMonitorService::class.java))
                                    isServiceRunning = false
                                    currentApp = "—"
                                    dnsState = "—"
                                } else {
                                    if (!hasUsageAccess) {
                                        Toast.makeText(context, "Нужен доступ к статистике!", Toast.LENGTH_SHORT).show()
                                        return@MainScreen
                                    }
                                    if (!hasSecureSettings) {
                                        Toast.makeText(context, "Нужно разрешение WRITE_SECURE_SETTINGS (через ADB)!", Toast.LENGTH_LONG).show()
                                        return@MainScreen
                                    }
                                    val serviceIntent = Intent(context, DnsMonitorService::class.java).apply {
                                        putExtra(DnsMonitorService.EXTRA_DNS_SERVER, dnsServer)
                                        putStringArrayListExtra(DnsMonitorService.EXTRA_TARGET_PACKAGES, ArrayList(selectedPackages))
                                    }
                                    ContextCompat.startForegroundService(context, serviceIntent)
                                    isServiceRunning = true
                                }
                            }
                        )
                    }
                    Screen.SETTINGS -> {
                        SettingsScreen(
                            hasUsageAccess = hasUsageAccess,
                            hasSecureSettings = hasSecureSettings,
                            hasBatteryOptimization = hasBatteryOptimization,
                            hasNotificationPermission = hasNotificationPermission,
                            dnsServer = dnsServer,
                            selectedPackages = selectedPackages,
                            installedApps = installedApps,
                            testDomain = testDomain,
                            pingResults = pingResults,
                            isTestingPings = isTestingPings,
                            isServiceRunning = isServiceRunning,
                            autoStartEnabled = autoStartEnabled,
                            onOpenSetupGuide = { showSetupGuide = true },
                            onRequestUsageAccess = {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            },
                            onRequestBatteryOptimization = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val activity = context as? ComponentActivity
                                    activity?.let {
                                        ActivityCompat.requestPermissions(
                                            it,
                                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                            1001
                                        )
                                    }
                                }
                            },
                            onRefreshPermissions = {
                                hasUsageAccess = checkUsageStatsPermission(context)
                                hasSecureSettings = checkWriteSecureSettings(context)
                                hasBatteryOptimization = checkBatteryOptimization(context)
                                hasNotificationPermission = checkNotificationPermission(context)
                            },
                            onDnsServerChange = {
                                dnsServer = it
                                prefs.edit().putString(Constants.KEY_DNS_SERVER, it).apply()
                            },
                            onApplyDns = {
                                val controller = DnsController(context)
                                val success = controller.enableDns(dnsServer)
                                if (success) {
                                    Toast.makeText(context, "DNS применён: $dnsServer", Toast.LENGTH_SHORT).show()
                                    if (isServiceRunning) {
                                        val serviceIntent = Intent(context, DnsMonitorService::class.java).apply {
                                            putExtra(DnsMonitorService.EXTRA_DNS_SERVER, dnsServer)
                                            putStringArrayListExtra(DnsMonitorService.EXTRA_TARGET_PACKAGES, ArrayList(selectedPackages))
                                        }
                                        ContextCompat.startForegroundService(context, serviceIntent)
                                    }
                                } else {
                                    Toast.makeText(context, "Ошибка! Нужно разрешение WRITE_SECURE_SETTINGS", Toast.LENGTH_LONG).show()
                                }
                            },
                            onTestDomainChange = {
                                testDomain = it
                                prefs.edit().putString("test_domain", it).apply()
                            },
                            onRunPingTest = { runPingTest() },
                            onOpenAppPicker = { showAppPicker = true },
                            onRemovePackage = { pkg ->
                                val updated = selectedPackages - pkg
                                selectedPackages = updated
                                prefs.edit().putStringSet(Constants.KEY_TARGET_PACKAGES, updated).apply()
                            },
                            onAutoStartToggle = { enabled ->
                                autoStartEnabled = enabled
                                prefs.edit().putBoolean(Constants.KEY_AUTO_START, enabled).apply()
                            }
                        )
                    }
                } // End when
                } // End Crossfade
            } // End Box
        } // End Scaffold
} // End DnsSwitcherApp