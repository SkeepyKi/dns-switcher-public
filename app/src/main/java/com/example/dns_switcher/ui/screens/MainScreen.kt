package com.example.dns_switcher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dns_switcher.ui.components.HeaderSection
import com.example.dns_switcher.ui.components.ServiceControlCard
import com.example.dns_switcher.ui.components.StatusCard

@Composable
fun MainScreen(
    isRunning: Boolean,
    currentApp: String,
    dnsState: String,
    excludedCount: Int,
    canStart: Boolean,
    onToggleService: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        HeaderSection()

        Spacer(modifier = Modifier.height(32.dp))

        // ── Управление службой ──
        ServiceControlCard(
            isRunning = isRunning,
            canStart = canStart,
            onToggleService = onToggleService
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Статус ──
        StatusCard(
            isRunning = isRunning,
            currentApp = currentApp,
            dnsState = dnsState,
            excludedCount = excludedCount
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
