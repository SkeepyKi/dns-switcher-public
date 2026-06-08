package com.example.dns_switcher

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.dns_switcher.ui.theme.DNSswitcherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    companion object {
        private const val PREFS_NAME = "dns_switcher_prefs"
        private const val KEY_DNS_SERVER = "dns_server"
        private const val KEY_TARGET_PACKAGES = "target_packages"
        private const val KEY_AUTO_START = "auto_start_enabled"
        private const val DEFAULT_DNS = "dns.adguard.com"
    }

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

// ──────────────────────────────────────────────────────────────
// Модель данных для приложения
// ──────────────────────────────────────────────────────────────

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: ImageBitmap?
)

fun Drawable.toImageBitmap(): ImageBitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

suspend fun loadInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)

    resolveInfos
        .map { ri ->
            AppInfo(
                name = ri.loadLabel(pm).toString(),
                packageName = ri.activityInfo.packageName,
                icon = try { ri.loadIcon(pm)?.toImageBitmap() } catch (_: Exception) { null }
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.name.lowercase() }
}

// ──────────────────────────────────────────────────────────────
// Цвета интерфейса
// ──────────────────────────────────────────────────────────────

private val DarkBackground = Color(0xFF0F1120)
private val CardBackground = Color(0xFF1A1D33)
private val CardBorder = Color(0xFF2A2E4A)
private val AccentCyan = Color(0xFF00D4FF)
private val AccentGreen = Color(0xFF00E676)
private val AccentRed = Color(0xFFFF5252)
private val AccentOrange = Color(0xFFFFAB40)
private val TextPrimary = Color(0xFFE8EAED)
private val TextSecondary = Color(0xFF9AA0B8)
private val InputBackground = Color(0xFF141729)
private val InputBorder = Color(0xFF2A2E4A)

// ──────────────────────────────────────────────────────────────
// Главный экран
// ──────────────────────────────────────────────────────────────

@Composable
fun DnsSwitcherApp() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("dns_switcher_prefs", Context.MODE_PRIVATE)

    var dnsServer by remember {
        mutableStateOf(prefs.getString("dns_server", "dns.adguard.com") ?: "dns.adguard.com")
    }
    var selectedPackages by remember {
        mutableStateOf(
            prefs.getStringSet("target_packages", DnsMonitorService.DEFAULT_PACKAGES)
                ?: DnsMonitorService.DEFAULT_PACKAGES
        )
    }
    var autoStartEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_start_enabled", false))
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

    // Загружаем список приложений
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        installedApps = loadInstalledApps(context)
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
                prefs.edit().putStringSet("target_packages", newSelection).apply()
            },
            onDismiss = { showAppPicker = false }
        )
    }

    Scaffold(
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            HeaderSection()

            Spacer(modifier = Modifier.height(24.dp))

            // ── Блок разрешений ──
            PermissionsCard(
                hasUsageAccess = hasUsageAccess,
                hasSecureSettings = hasSecureSettings,
                hasBatteryOptimization = hasBatteryOptimization,
                hasNotificationPermission = hasNotificationPermission,
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
                onRefresh = {
                    hasUsageAccess = checkUsageStatsPermission(context)
                    hasSecureSettings = checkWriteSecureSettings(context)
                    hasBatteryOptimization = checkBatteryOptimization(context)
                    hasNotificationPermission = checkNotificationPermission(context)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Настройки DNS ──
            SettingsCard(
                dnsServer = dnsServer,
                selectedPackages = selectedPackages,
                installedApps = installedApps,
                onDnsServerChange = {
                    dnsServer = it
                    prefs.edit().putString("dns_server", it).apply()
                },
                onOpenAppPicker = { showAppPicker = true },
                onRemovePackage = { pkg ->
                    val updated = selectedPackages - pkg
                    selectedPackages = updated
                    prefs.edit().putStringSet("target_packages", updated).apply()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Управление службой ──
            ServiceControlCard(
                isRunning = isServiceRunning,
                autoStartEnabled = autoStartEnabled,
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
                            return@ServiceControlCard
                        }
                        if (!hasSecureSettings) {
                            Toast.makeText(context, "Нужно разрешение WRITE_SECURE_SETTINGS (через ADB)!", Toast.LENGTH_LONG).show()
                            return@ServiceControlCard
                        }
                        val serviceIntent = Intent(context, DnsMonitorService::class.java).apply {
                            putExtra(DnsMonitorService.EXTRA_DNS_SERVER, dnsServer)
                            putStringArrayListExtra(DnsMonitorService.EXTRA_TARGET_PACKAGES, ArrayList(selectedPackages))
                        }
                        ContextCompat.startForegroundService(context, serviceIntent)
                        isServiceRunning = true
                    }
                },
                onAutoStartToggle = { enabled ->
                    autoStartEnabled = enabled
                    prefs.edit().putBoolean("auto_start_enabled", enabled).apply()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Статус ──
            StatusCard(
                isRunning = isServiceRunning,
                currentApp = currentApp,
                dnsState = dnsState,
                excludedCount = selectedPackages.size
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Заголовок
// ──────────────────────────────────────────────────────────────

@Composable
private fun HeaderSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(AccentCyan, Color(0xFF6C63FF))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🛡️",
                fontSize = 28.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "DNS Switcher",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Text(
            text = "Автоматическое управление Private DNS",
            fontSize = 14.sp,
            color = TextSecondary
        )
    }
}

// ──────────────────────────────────────────────────────────────
// Карточка разрешений
// ──────────────────────────────────────────────────────────────

@Composable
private fun PermissionsCard(
    hasUsageAccess: Boolean,
    hasSecureSettings: Boolean,
    hasBatteryOptimization: Boolean,
    hasNotificationPermission: Boolean,
    onOpenSetupGuide: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRefresh: () -> Unit
) {
    val allGranted = hasUsageAccess && hasSecureSettings && hasBatteryOptimization && hasNotificationPermission
    val grantedCount = listOf(hasUsageAccess, hasSecureSettings, hasBatteryOptimization, hasNotificationPermission).count { it }

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "РАЗРЕШЕНИЯ",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
                letterSpacing = 2.sp
            )
            Text(
                text = if (allGranted) "✓ Все выданы" else "$grantedCount / 4",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (allGranted) AccentGreen else AccentOrange
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        PermissionRow(
            label = "Статистика использования",
            description = "Определение активного приложения",
            granted = hasUsageAccess,
            actionLabel = "Открыть",
            onAction = onRequestUsageAccess
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow(
            label = "Системные настройки DNS",
            description = "WRITE_SECURE_SETTINGS",
            granted = hasSecureSettings,
            actionLabel = null,
            onAction = {}
        )
        if (!hasSecureSettings) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "adb shell pm grant com.example.dns_switcher android.permission.WRITE_SECURE_SETTINGS",
                fontSize = 10.sp,
                color = AccentOrange,
                lineHeight = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentOrange.copy(alpha = 0.1f))
                    .padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow(
            label = "Без ограничений батареи",
            description = "Работа в фоне без ограничений",
            granted = hasBatteryOptimization,
            actionLabel = "Откл.",
            onAction = onRequestBatteryOptimization
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionRow(
            label = "Уведомления",
            description = "Для статуса службы",
            granted = hasNotificationPermission,
            actionLabel = "Разрешить",
            onAction = onRequestNotificationPermission
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onRefresh,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allGranted) AccentGreen.copy(alpha = 0.15f) else CardBorder,
                contentColor = if (allGranted) AccentGreen else TextSecondary
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (allGranted) "✓ Все разрешения выданы" else "Обновить статус",
                fontSize = 13.sp
            )
        }

        if (!allGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onOpenSetupGuide,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan.copy(alpha = 0.12f),
                    contentColor = AccentCyan
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "📱  Настроить без ПК",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    description: String = "",
    granted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (granted) AccentGreen else AccentRed,
        animationSpec = tween(500), label = "indicator"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (granted) AccentGreen.copy(alpha = 0.05f) else AccentRed.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(indicatorColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            if (description.isNotEmpty()) {
                Text(text = description, fontSize = 11.sp, color = TextSecondary)
            }
        }
        if (!granted && actionLabel != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan.copy(alpha = 0.15f),
                    contentColor = AccentCyan
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(actionLabel, fontSize = 12.sp)
            }
        } else if (granted) {
            Text("✓", fontSize = 16.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Карточка настроек (DNS + выбор приложений)
// ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsCard(
    dnsServer: String,
    selectedPackages: Set<String>,
    installedApps: List<AppInfo>,
    onDnsServerChange: (String) -> Unit,
    onOpenAppPicker: () -> Unit,
    onRemovePackage: (String) -> Unit
) {
    GlassCard {
        Text(
            text = "НАСТРОЙКИ",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AccentCyan,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // DNS-сервер
        Text(
            text = "DNS-сервер",
            fontSize = 13.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        StyledTextField(
            value = dnsServer,
            onValueChange = onDnsServerChange,
            placeholder = "dns.adguard.com"
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Исключения (приложения)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Исключения из DNS",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "DNS будет отключён для этих приложений",
                    fontSize = 11.sp,
                    color = TextSecondary.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Чипы выбранных приложений
        if (selectedPackages.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                selectedPackages.forEach { pkg ->
                    val appInfo = installedApps.find { it.packageName == pkg }
                    val displayName = appInfo?.name ?: pkg.substringAfterLast(".")

                    AppChip(
                        name = displayName,
                        icon = appInfo?.icon,
                        onRemove = { onRemovePackage(pkg) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Кнопка добавления
        Button(
            onClick = onOpenAppPicker,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentCyan.copy(alpha = 0.12f),
                contentColor = AccentCyan
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (selectedPackages.isEmpty()) "＋  Выбрать приложения" else "＋  Изменить список",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AppChip(
    name: String,
    icon: ImageBitmap?,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AccentCyan.copy(alpha = 0.1f))
            .border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = name,
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
            text = name,
            fontSize = 12.sp,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Кнопка удаления
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(AccentRed.copy(alpha = 0.15f))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Text("✕", fontSize = 10.sp, color = AccentRed, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(placeholder, color = TextSecondary.copy(alpha = 0.5f), fontSize = 14.sp)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AccentCyan,
            focusedBorderColor = AccentCyan,
            unfocusedBorderColor = InputBorder,
            focusedContainerColor = InputBackground,
            unfocusedContainerColor = InputBackground,
        ),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
    )
}

// ──────────────────────────────────────────────────────────────
// Диалог выбора приложений
// ──────────────────────────────────────────────────────────────

@Composable
private fun AppPickerDialog(
    installedApps: List<AppInfo>,
    selectedPackages: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var localSelection by remember { mutableStateOf(selectedPackages) }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            val query = searchQuery.lowercase()
            installedApps.filter {
                it.name.lowercase().contains(query) ||
                it.packageName.lowercase().contains(query)
            }
        }
    }

    Dialog(
        onDismissRequest = {
            onSelectionChanged(localSelection)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
        ) {
            // ── Шапка диалога ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Выбор приложений",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Выбрано: ${localSelection.size}",
                            fontSize = 13.sp,
                            color = AccentCyan
                        )
                    }

                    Button(
                        onClick = {
                            onSelectionChanged(localSelection)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Готово", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Поиск ──
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("🔍  Поиск по названию...", color = TextSecondary.copy(alpha = 0.5f), fontSize = 14.sp)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentCyan,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = InputBorder,
                        focusedContainerColor = InputBackground,
                        unfocusedContainerColor = InputBackground,
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                )
            }

            // ── Список приложений ──
            if (filteredApps.isEmpty() && installedApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Загрузка приложений...", color = TextSecondary, fontSize = 14.sp)
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("😕", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ничего не найдено", color = TextSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    // Сначала — выбранные
                    val selected = filteredApps.filter { it.packageName in localSelection }
                    val notSelected = filteredApps.filter { it.packageName !in localSelection }

                    if (selected.isNotEmpty()) {
                        item {
                            Text(
                                text = "ВЫБРАННЫЕ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen,
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        items(selected, key = { it.packageName }) { app ->
                            AppListItem(
                                app = app,
                                isSelected = true,
                                onToggle = {
                                    localSelection = localSelection - app.packageName
                                }
                            )
                        }
                    }

                    if (notSelected.isNotEmpty()) {
                        item {
                            Text(
                                text = "ВСЕ ПРИЛОЖЕНИЯ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        items(notSelected, key = { it.packageName }) { app ->
                            AppListItem(
                                app = app,
                                isSelected = false,
                                onToggle = {
                                    localSelection = localSelection + app.packageName
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) AccentCyan.copy(alpha = 0.08f) else Color.Transparent,
        animationSpec = tween(300), label = "appBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Иконка приложения
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = app.name,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CardBorder),
                contentAlignment = Alignment.Center
            ) {
                Text("📱", fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Название и пакет
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Чекбокс
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = AccentCyan,
                uncheckedColor = CardBorder,
                checkmarkColor = DarkBackground
            )
        )
    }
}

// ──────────────────────────────────────────────────────────────
// Карточка управления службой
// ──────────────────────────────────────────────────────────────

@Composable
private fun ServiceControlCard(
    isRunning: Boolean,
    autoStartEnabled: Boolean,
    canStart: Boolean,
    onToggleService: () -> Unit,
    onAutoStartToggle: (Boolean) -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = if (isRunning) AccentRed else AccentGreen,
        animationSpec = tween(400), label = "button"
    )

    GlassCard {
        Text(
            text = "УПРАВЛЕНИЕ",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AccentCyan,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onToggleService,
            enabled = canStart || isRunning,
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = Color.White,
                disabledContainerColor = CardBorder,
                disabledContentColor = TextSecondary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = if (isRunning) "⏹  Остановить мониторинг" else "▶  Запустить мониторинг",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (!canStart && !isRunning) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Выдайте все разрешения для запуска",
                fontSize = 12.sp,
                color = AccentOrange,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Автозапуск",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = "Запускать после перезагрузки",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Switch(
                checked = autoStartEnabled,
                onCheckedChange = onAutoStartToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentCyan,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = CardBorder
                )
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Карточка статуса
// ──────────────────────────────────────────────────────────────

@Composable
private fun StatusCard(
    isRunning: Boolean,
    currentApp: String,
    dnsState: String,
    excludedCount: Int
) {
    val statusDotColor by animateColorAsState(
        targetValue = if (isRunning) AccentGreen else TextSecondary,
        animationSpec = tween(500), label = "statusDot"
    )
    val pulseAlpha by animateFloatAsState(
        targetValue = if (isRunning) 1f else 0.4f,
        animationSpec = tween(800), label = "pulse"
    )

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "СТАТУС",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(statusDotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isRunning) "Активен" else "Остановлен",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = statusDotColor
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        StatusRow(label = "Мониторинг", value = if (isRunning) "Запущен" else "Остановлен")
        Spacer(modifier = Modifier.height(8.dp))
        StatusRow(label = "Текущее приложение", value = currentApp)
        Spacer(modifier = Modifier.height(8.dp))
        StatusRow(label = "Состояние DNS", value = dnsState)
        Spacer(modifier = Modifier.height(8.dp))
        StatusRow(label = "Исключений", value = "$excludedCount приложений")
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(InputBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f)
        )
    }
}

// ──────────────────────────────────────────────────────────────
// Стеклянная карточка
// ──────────────────────────────────────────────────────────────

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = CardBorder,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Визард настройки разрешений без ПК
// ──────────────────────────────────────────────────────────────

@Composable
private fun SetupWizardDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val packageName = context.packageName

    val cmd1 = "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
    val cmd2 = "appops set $packageName android:get_usage_stats allow"

    var copiedIndex by remember { mutableStateOf(-1) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkBackground)
                .border(1.dp, CardBorder, RoundedCornerShape(24.dp))
        ) {
            // ── Шапка ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Настройка без ПК",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Выдача разрешений через Brevent",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Готово", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            // ── Шаги ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SetupStep(
                    number = 1,
                    title = "Скачай Brevent",
                    description = "Бесплатное приложение с ADB-терминалом прямо на телефоне (без ПК)",
                    buttonLabel = "Открыть Google Play",
                    onAction = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=me.piebridge.brevent")))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=me.piebridge.brevent")))
                        }
                    }
                )

                SetupStep(
                    number = 2,
                    title = "Включи режим разработчика",
                    description = "Настройки → О телефоне → Нажми 7 раз на «Версия ОС» или «Номер сборки»",
                    buttonLabel = "Открыть «О телефоне»",
                    onAction = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                )

                SetupStep(
                    number = 3,
                    title = "Включи беспроводную отладку",
                    description = "Настройки → Для разработчиков → Отладка по USB ✓ → Беспроводная отладка ✓",
                    buttonLabel = "Настройки разработчика",
                    onAction = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                )

                SetupStep(
                    number = 4,
                    title = "Подключи Brevent",
                    description = "Открой Brevent → Включи беспроводную отладку → Следуй инструкции для сопряжения в приложении",
                    buttonLabel = null,
                    onAction = null
                )

                CommandStep(
                    number = 5,
                    title = "Команда: настройки DNS",
                    description = "Скопируй и вставь в терминал Brevent",
                    command = cmd1,
                    isCopied = copiedIndex == 5,
                    onCopy = {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("ADB", cmd1))
                        copiedIndex = 5
                        Toast.makeText(context, "Команда скопирована!", Toast.LENGTH_SHORT).show()
                    }
                )

                CommandStep(
                    number = 6,
                    title = "Команда: статистика",
                    description = "Скопируй и вставь в терминал Brevent",
                    command = cmd2,
                    isCopied = copiedIndex == 6,
                    onCopy = {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("ADB", cmd2))
                        copiedIndex = 6
                        Toast.makeText(context, "Команда скопирована!", Toast.LENGTH_SHORT).show()
                    }
                )

                // Финальная заметка
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AccentGreen.copy(alpha = 0.08f))
                        .border(1.dp, AccentGreen.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("✅", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "После выполнения команд вернись сюда и нажми «Готово» — разрешения обновятся автоматически",
                        fontSize = 13.sp,
                        color = TextPrimary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    description: String,
    buttonLabel: String?,
    onAction: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AccentCyan, Color(0xFF6C63FF))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$number",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 44.dp)
        )

        if (buttonLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan.copy(alpha = 0.12f),
                    contentColor = AccentCyan
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(start = 44.dp)
            ) {
                Text(buttonLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CommandStep(
    number: Int,
    title: String,
    description: String,
    command: String,
    isCopied: Boolean,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AccentCyan, Color(0xFF6C63FF))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$number",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 44.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Блок с командой
        Text(
            text = command,
            fontSize = 11.sp,
            color = AccentOrange,
            lineHeight = 15.sp,
            modifier = Modifier
                .padding(start = 44.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AccentOrange.copy(alpha = 0.08f))
                .border(1.dp, AccentOrange.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onCopy,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isCopied) AccentGreen.copy(alpha = 0.15f) else AccentCyan.copy(alpha = 0.12f),
                contentColor = if (isCopied) AccentGreen else AccentCyan
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.padding(start = 44.dp)
        ) {
            Text(
                text = if (isCopied) "✓ Скопировано" else "📋  Копировать команду",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Проверка разрешений
// ──────────────────────────────────────────────────────────────

private fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun checkWriteSecureSettings(context: Context): Boolean {
    return try {
        val resolver = context.contentResolver
        val currentMode = Settings.Global.getString(resolver, "private_dns_mode") ?: "off"
        Settings.Global.putString(resolver, "private_dns_mode", currentMode)
        true
    } catch (e: SecurityException) {
        false
    }
}

private fun checkBatteryOptimization(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.areNotificationsEnabled()
    }
}