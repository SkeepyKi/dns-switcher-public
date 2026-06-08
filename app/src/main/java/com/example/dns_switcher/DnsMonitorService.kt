package com.example.dns_switcher

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Фоновая служба мониторинга, работающая в режиме Foreground.
 *
 * Каждые 1.5 секунды проверяет, какое приложение находится на переднем плане:
 * - Если это целевая игра (по умолчанию Brawl Stars) — отключает Private DNS.
 * - Если запущено другое приложение — включает Private DNS с указанным сервером.
 *
 * Отображает постоянное уведомление с текущим статусом.
 */
class DnsMonitorService : Service() {

    companion object {
        private const val TAG = "DnsMonitorService"
        private const val CHANNEL_ID = "dns_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 1500L
        private const val WATCHDOG_REQUEST_CODE = 7777
        private const val WATCHDOG_INTERVAL_MS = 30_000L // 30 секунд

        const val EXTRA_DNS_SERVER = "extra_dns_server"
        const val EXTRA_TARGET_PACKAGES = "extra_target_packages"

        const val ACTION_STATUS_UPDATE = "com.example.dns_switcher.STATUS_UPDATE"
        const val EXTRA_CURRENT_APP = "extra_current_app"
        const val EXTRA_DNS_STATE = "extra_dns_state"
        const val EXTRA_IS_RUNNING = "extra_is_running"

        private const val PREFS_NAME = "dns_switcher_prefs"
        private const val KEY_DNS_SERVER = "dns_server"
        private const val KEY_TARGET_PACKAGES = "target_packages"
        private const val DEFAULT_DNS = "dns.adguard.com"
        val DEFAULT_PACKAGES = setOf("com.supercell.brawlstars")
    }

    private lateinit var dnsController: DnsController
    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())
    private var isTaskRemoved = false
    private var isRunning = false

    private var dnsServer: String = DEFAULT_DNS
    private var targetPackages: Set<String> = DEFAULT_PACKAGES
    private var lastForegroundApp: String = ""
    private var isDnsEnabled = true

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            val currentApp = getForegroundApp()
            if (currentApp.isNotEmpty()) {
                lastForegroundApp = currentApp

                if (currentApp in targetPackages) {
                    // Целевая игра на переднем плане — отключаем DNS
                    if (isDnsEnabled) {
                        dnsController.disableDns()
                        isDnsEnabled = false
                        Log.d(TAG, "DNS отключён для: $currentApp")
                        updateNotification("DNS ВЫКЛ — $currentApp")
                    }
                } else {
                    // Другое приложение — включаем DNS
                    if (!isDnsEnabled) {
                        dnsController.enableDns(dnsServer)
                        isDnsEnabled = true
                        Log.d(TAG, "DNS включён ($dnsServer) — $currentApp")
                        updateNotification("DNS ВКЛ ($dnsServer)")
                    }
                }

                // Отправляем broadcast с обновлением статуса для UI
                sendStatusBroadcast(currentApp)
            }

            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        dnsController = DnsController(this)
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Считываем параметры из Intent или SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        dnsServer = intent?.getStringExtra(EXTRA_DNS_SERVER)
            ?: prefs.getString(KEY_DNS_SERVER, DEFAULT_DNS)!!
        targetPackages = intent?.getStringArrayListExtra(EXTRA_TARGET_PACKAGES)?.toSet()
            ?: prefs.getStringSet(KEY_TARGET_PACKAGES, DEFAULT_PACKAGES)!!

        // Сохраняем в SharedPreferences для будущего использования (BootReceiver + QS Tile)
        prefs.edit()
            .putString(KEY_DNS_SERVER, dnsServer)
            .putStringSet(KEY_TARGET_PACKAGES, targetPackages)
            .putBoolean("service_running", true)
            .apply()

        // Запускаем Foreground с минимальным уведомлением
        val notification = buildNotification("Мониторинг активен")
        startForeground(NOTIFICATION_ID, notification)

        // Сбрасываем флаг смахивания (для корректной работы onDestroy при ручной остановке)
        isTaskRemoved = false

        // Запускаем цикл опроса
        isRunning = true
        isDnsEnabled = true
        dnsController.enableDns(dnsServer)
        handler.post(pollRunnable)

        // Обновляем QS Tile
        DnsQsTileService.requestTileUpdate(this)

        // Ставим watchdog-алярм на 2 минуты — если процесс убьют без onTaskRemoved,
        // алярм перезапустит сервис. Каждый onStartCommand обновляет алярм.
        scheduleWatchdog()

        Log.d(TAG, "Служба запущена. DNS: $dnsServer, Исключения: ${targetPackages.size} приложений")

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)

        if (!isTaskRemoved) {
            // Ручная остановка — сбрасываем всё и отменяем watchdog
            isRunning = false
            cancelWatchdog()
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit().putBoolean("service_running", false).apply()
            DnsQsTileService.requestTileUpdate(this)
            sendStatusBroadcast("")
        }

        Log.d(TAG, "Служба остановлена (taskRemoved=$isTaskRemoved)")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Вызывается при смахивании приложения из списка недавних.
     * Перезапускает службу, чтобы мониторинг продолжал работать.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        isTaskRemoved = true
        Log.d(TAG, "Приложение смахнуто — планируем перезапуск")

        val restartIntent = Intent(this, DnsMonitorService::class.java).apply {
            putExtra(EXTRA_DNS_SERVER, dnsServer)
            putStringArrayListExtra(EXTRA_TARGET_PACKAGES, ArrayList(targetPackages))
        }

        // Способ 1: прямой перезапуск (срабатывает при «закрыть все»)
        try {
            androidx.core.content.ContextCompat.startForegroundService(this, restartIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Прямой перезапуск не удался: ${e.message}")
        }

        // Способ 2: AlarmManager как бэкап (срабатывает при одиночном смахивании)
        val pendingIntent = PendingIntent.getForegroundService(
            this,
            System.currentTimeMillis().toInt(),
            restartIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 1000,
            pendingIntent
        )
    }

    private fun getWatchdogPendingIntent(): PendingIntent {
        val intent = Intent(this, DnsMonitorService::class.java)
        return PendingIntent.getForegroundService(
            this,
            WATCHDOG_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleWatchdog() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
            getWatchdogPendingIntent()
        )
        Log.d(TAG, "Watchdog запланирован через ${WATCHDOG_INTERVAL_MS / 1000} сек")
    }

    private fun cancelWatchdog() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(getWatchdogPendingIntent())
        Log.d(TAG, "Watchdog отменён")
    }

    /**
     * Получает имя пакета приложения, которое сейчас на переднем плане.
     *
     * Использует UsageEvents.queryEvents() вместо queryUsageStats(), т.к.
     * queryUsageStats возвращает агрегированную статистику с «залипающим» lastTimeUsed,
     * а queryEvents даёт реальные события перехода (ACTIVITY_RESUMED).
     */
    private fun getForegroundApp(): String {
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 30_000 // 30 секунд — достаточно для захвата перехода

        val usageEvents = usageStatsManager.queryEvents(beginTime, endTime)
        var currentApp = ""

        val event = android.app.usage.UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            // ACTIVITY_RESUMED = приложение вышло на передний план
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                currentApp = event.packageName
            }
        }

        return currentApp
    }

    /**
     * Создаёт канал уведомлений для Android 8+.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DNS Мониторинг",
            NotificationManager.IMPORTANCE_MIN  // Минимальная заметность
        ).apply {
            description = "Служебное уведомление для работы в фоне"
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * Создаёт минимальное Notification для Foreground-службы.
     * Уведомление обязательно для Foreground Service, но мы делаем его
     * максимально незаметным (IMPORTANCE_MIN — скрыто в тихом разделе).
     */
    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DNS Switcher")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Обновляет текст уведомления.
     */
    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Отправляет broadcast с текущим статусом для обновления UI.
     */
    private fun sendStatusBroadcast(currentApp: String) {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_CURRENT_APP, currentApp)
            putExtra(EXTRA_DNS_STATE, if (isDnsEnabled) "ВКЛ ($dnsServer)" else "ВЫКЛ")
            putExtra(EXTRA_IS_RUNNING, isRunning)
        }
        sendBroadcast(intent)
    }
}
