package com.example.dns_switcher.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dns_switcher.AppInfo
import com.example.dns_switcher.ui.theme.AppColors

@Composable
fun AppPickerDialog(
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
                .background(AppColors.DarkBackground)
                .border(1.dp, AppColors.CardBorder, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.CardBackground)
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
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "Выбрано: ${localSelection.size}",
                            fontSize = 13.sp,
                            color = AppColors.AccentCyan
                        )
                    }

                    Button(
                        onClick = {
                            onSelectionChanged(localSelection)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.AccentCyan,
                            contentColor = AppColors.DarkBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Готово", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("🔍  Поиск по названию...", color = AppColors.TextSecondary.copy(alpha = 0.5f), fontSize = 14.sp)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        cursorColor = AppColors.AccentCyan,
                        focusedBorderColor = AppColors.AccentCyan,
                        unfocusedBorderColor = AppColors.InputBorder,
                        focusedContainerColor = AppColors.InputBackground,
                        unfocusedContainerColor = AppColors.InputBackground,
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                )
            }

            if (filteredApps.isEmpty() && installedApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Загрузка приложений...", color = AppColors.TextSecondary, fontSize = 14.sp)
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
                        Text("Ничего не найдено", color = AppColors.TextSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    val selected = filteredApps.filter { it.packageName in localSelection }
                    val notSelected = filteredApps.filter { it.packageName !in localSelection }

                    if (selected.isNotEmpty()) {
                        item {
                            Text(
                                text = "ВЫБРАННЫЕ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.AccentGreen,
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
                                color = AppColors.TextSecondary,
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
fun AppListItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) AppColors.AccentCyan.copy(alpha = 0.08f) else Color.Transparent,
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
                    .background(AppColors.CardBorder),
                contentAlignment = Alignment.Center
            ) {
                Text("📱", fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                fontSize = 11.sp,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = AppColors.AccentCyan,
                uncheckedColor = AppColors.CardBorder,
                checkmarkColor = AppColors.DarkBackground
            )
        )
    }
}

@Composable
fun SetupWizardDialog(
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
                .background(AppColors.DarkBackground)
                .border(1.dp, AppColors.CardBorder, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.CardBackground)
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
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = "Выдача разрешений через Brevent",
                            fontSize = 13.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.AccentCyan,
                            contentColor = AppColors.DarkBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Готово", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

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

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.AccentGreen.copy(alpha = 0.08f))
                        .border(1.dp, AppColors.AccentGreen.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text("✅", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "После выполнения команд вернись сюда и нажми «Готово» — разрешения обновятся автоматически",
                        fontSize = 13.sp,
                        color = AppColors.TextPrimary,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SetupStep(
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
            .background(AppColors.CardBackground)
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AppColors.AccentCyan, Color(0xFF6C63FF))
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
                color = AppColors.TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            fontSize = 13.sp,
            color = AppColors.TextSecondary,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 44.dp)
        )

        if (buttonLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.AccentCyan.copy(alpha = 0.12f),
                    contentColor = AppColors.AccentCyan
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
fun CommandStep(
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
            .background(AppColors.CardBackground)
            .border(1.dp, AppColors.CardBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AppColors.AccentCyan, Color(0xFF6C63FF))
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
                color = AppColors.TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            fontSize = 13.sp,
            color = AppColors.TextSecondary,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 44.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = command,
            fontSize = 11.sp,
            color = AppColors.AccentOrange,
            lineHeight = 15.sp,
            modifier = Modifier
                .padding(start = 44.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.AccentOrange.copy(alpha = 0.08f))
                .border(1.dp, AppColors.AccentOrange.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onCopy,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isCopied) AppColors.AccentGreen.copy(alpha = 0.15f) else AppColors.AccentCyan.copy(alpha = 0.12f),
                contentColor = if (isCopied) AppColors.AccentGreen else AppColors.AccentCyan
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
