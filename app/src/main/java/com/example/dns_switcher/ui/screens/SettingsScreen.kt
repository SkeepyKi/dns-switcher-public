package com.example.dns_switcher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dns_switcher.AppInfo
import com.example.dns_switcher.ui.components.*

@Composable
fun SettingsScreen(
    hasUsageAccess: Boolean,
    hasSecureSettings: Boolean,
    hasBatteryOptimization: Boolean,
    hasNotificationPermission: Boolean,
    dnsServer: String,
    selectedPackages: Set<String>,
    installedApps: List<AppInfo>,
    testDomain: String,
    pingResults: Map<String, String>,
    isTestingPings: Boolean,
    isServiceRunning: Boolean,
    autoStartEnabled: Boolean,
    onOpenSetupGuide: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onDnsServerChange: (String) -> Unit,
    onApplyDns: () -> Unit,
    onTestDomainChange: (String) -> Unit,
    onRunPingTest: () -> Unit,
    onOpenAppPicker: () -> Unit,
    onRemovePackage: (String) -> Unit,
    onAutoStartToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        // ── Блок разрешений ──
        PermissionsCard(
            hasUsageAccess = hasUsageAccess,
            hasSecureSettings = hasSecureSettings,
            hasBatteryOptimization = hasBatteryOptimization,
            hasNotificationPermission = hasNotificationPermission,
            onOpenSetupGuide = onOpenSetupGuide,
            onRequestUsageAccess = onRequestUsageAccess,
            onRequestBatteryOptimization = onRequestBatteryOptimization,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRefresh = onRefreshPermissions
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Автозапуск ──
        AutoStartCard(
            autoStartEnabled = autoStartEnabled,
            onAutoStartToggle = onAutoStartToggle
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Настройки DNS ──
        SettingsCard(
            dnsServer = dnsServer,
            selectedPackages = selectedPackages,
            installedApps = installedApps,
            testDomain = testDomain,
            pingResults = pingResults,
            isTestingPings = isTestingPings,
            isServiceRunning = isServiceRunning,
            onDnsServerChange = onDnsServerChange,
            onApplyDns = onApplyDns,
            onTestDomainChange = onTestDomainChange,
            onRunPingTest = onRunPingTest,
            onOpenAppPicker = onOpenAppPicker,
            onRemovePackage = onRemovePackage
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
