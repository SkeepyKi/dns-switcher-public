package com.example.dns_switcher.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.dns_switcher.AppInfo
import com.example.dns_switcher.ui.theme.AppColors

@Composable
fun HeaderSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(AppColors.AccentCyan, Color(0xFF6C63FF))
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
            color = AppColors.TextPrimary
        )

        Text(
            text = "Автоматическое управление Private DNS",
            fontSize = 14.sp,
            color = AppColors.TextSecondary
        )
    }
}

@Composable
fun PermissionsCard(
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
    val requiredGranted = hasUsageAccess && hasSecureSettings && hasBatteryOptimization
    val requiredGrantedCount = listOf(hasUsageAccess, hasSecureSettings, hasBatteryOptimization).count { it }
    var isExpanded by remember { mutableStateOf(!requiredGranted) }

    LaunchedEffect(requiredGranted) {
        if (requiredGranted) isExpanded = false
    }

    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "РАЗРЕШЕНИЯ",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.AccentCyan,
                letterSpacing = 2.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (requiredGranted) "✓ Все нужные выданы" else "$requiredGrantedCount / 3",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (requiredGranted) AppColors.AccentGreen else AppColors.AccentOrange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
            Column {
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
                        color = AppColors.AccentOrange,
                        lineHeight = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.AccentOrange.copy(alpha = 0.1f))
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
                
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = AppColors.CardBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                PermissionRow(
                    label = "Уведомления 🔔 (По желанию)",
                    description = "Необязательно. Показывает статус работы службы в шторке.",
                    granted = hasNotificationPermission,
                    actionLabel = "Разрешить",
                    onAction = onRequestNotificationPermission
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (requiredGranted) AppColors.AccentGreen.copy(alpha = 0.15f) else AppColors.CardBorder,
                        contentColor = if (requiredGranted) AppColors.AccentGreen else AppColors.TextSecondary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Обновить статус",
                        fontSize = 13.sp
                    )
                }

                if (!requiredGranted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onOpenSetupGuide,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.AccentCyan.copy(alpha = 0.12f),
                            contentColor = AppColors.AccentCyan
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
    }
}

@Composable
fun PermissionRow(
    label: String,
    description: String = "",
    granted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (granted) AppColors.AccentGreen else AppColors.AccentRed,
        animationSpec = tween(500), label = "indicator"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (granted) AppColors.AccentGreen.copy(alpha = 0.05f) else AppColors.AccentRed.copy(alpha = 0.05f))
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
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
            if (description.isNotEmpty()) {
                Text(text = description, fontSize = 11.sp, color = AppColors.TextSecondary)
            }
        }
        if (!granted && actionLabel != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.AccentCyan.copy(alpha = 0.15f),
                    contentColor = AppColors.AccentCyan
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(actionLabel, fontSize = 12.sp)
            }
        } else if (granted) {
            Text("✓", fontSize = 16.sp, color = AppColors.AccentGreen, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCard(
    dnsServer: String,
    selectedPackages: Set<String>,
    installedApps: List<AppInfo>,
    testDomain: String,
    pingResults: Map<String, String>,
    isTestingPings: Boolean,
    isServiceRunning: Boolean,
    onDnsServerChange: (String) -> Unit,
    onApplyDns: () -> Unit,
    onTestDomainChange: (String) -> Unit,
    onRunPingTest: () -> Unit,
    onOpenAppPicker: () -> Unit,
    onRemovePackage: (String) -> Unit
) {
    GlassCard {
        Text(
            text = "НАСТРОЙКИ DNS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.AccentCyan,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Текущий DNS-сервер",
            fontSize = 13.sp,
            color = AppColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        StyledTextField(
            value = dnsServer,
            onValueChange = onDnsServerChange,
            placeholder = "dns.google"
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(AppColors.AccentCyan.copy(alpha = 0.15f), AppColors.AccentGreen.copy(alpha = 0.10f))
                    )
                )
                .border(1.dp, AppColors.AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .clickable { onApplyDns() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✅ Применить DNS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.AccentCyan
            )
            if (isServiceRunning) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "(+ обновить службу)",
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Пинг текущего DNS",
                fontSize = 13.sp,
                color = AppColors.TextSecondary,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = if (isTestingPings) "Тестирование..." else "Обновить пинг 🔄",
                fontSize = 11.sp,
                color = if (isTestingPings) AppColors.AccentOrange else AppColors.AccentCyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = !isTestingPings) { onRunPingTest() }
                    .padding(4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        if (dnsServer.isNotBlank()) {
            val customPing = pingResults[dnsServer.trim()] ?: "—"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.AccentCyan.copy(alpha = 0.08f))
                    .border(
                        1.dp,
                        AppColors.AccentCyan.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Выбранный DNS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = dnsServer.trim(),
                        fontSize = 11.sp,
                        color = AppColors.TextSecondary
                    )
                }

                Text(
                    text = customPing,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.getPingColor(customPing)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Домен для проверки пинга",
            fontSize = 13.sp,
            color = AppColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        StyledTextField(
            value = testDomain,
            onValueChange = onTestDomainChange,
            placeholder = "gemini.google.com"
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Исключения из DNS",
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "DNS будет отключён для этих приложений",
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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

        Button(
            onClick = onOpenAppPicker,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.AccentCyan.copy(alpha = 0.12f),
                contentColor = AppColors.AccentCyan
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
fun ServiceControlCard(
    isRunning: Boolean,
    canStart: Boolean,
    onToggleService: () -> Unit
) {
    val buttonColor by animateColorAsState(
        targetValue = if (isRunning) AppColors.AccentRed else AppColors.AccentGreen,
        animationSpec = tween(400), label = "button"
    )

    GlassCard {
        Text(
            text = "УПРАВЛЕНИЕ",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.AccentCyan,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onToggleService,
            enabled = canStart || isRunning,
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = Color.White,
                disabledContainerColor = AppColors.CardBorder,
                disabledContentColor = AppColors.TextSecondary
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
                text = "Выдайте все разрешения в Настройках",
                fontSize = 12.sp,
                color = AppColors.AccentOrange,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun AutoStartCard(
    autoStartEnabled: Boolean,
    onAutoStartToggle: (Boolean) -> Unit
) {
    GlassCard {
        Text(
            text = "АВТОЗАПУСК",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.AccentCyan,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Включать после перезагрузки",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Мониторинг будет запускаться сам",
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary
                )
            }

            Switch(
                checked = autoStartEnabled,
                onCheckedChange = onAutoStartToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppColors.AccentCyan,
                    uncheckedThumbColor = AppColors.TextSecondary,
                    uncheckedTrackColor = AppColors.CardBorder
                )
            )
        }
    }
}

@Composable
fun StatusCard(
    isRunning: Boolean,
    currentApp: String,
    dnsState: String,
    excludedCount: Int
) {
    val statusDotColor by animateColorAsState(
        targetValue = if (isRunning) AppColors.AccentGreen else AppColors.TextSecondary,
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
                color = AppColors.AccentCyan,
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
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.InputBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = AppColors.TextSecondary,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = AppColors.CardBorder,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppColors.CardBackground.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}
