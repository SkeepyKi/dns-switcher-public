package com.example.dns_switcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver для автозапуска службы мониторинга DNS после перезагрузки устройства.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "dns_switcher_prefs"
        private const val KEY_DNS_SERVER = "dns_server"
        private const val KEY_TARGET_PACKAGES = "target_packages"
        private const val KEY_AUTO_START = "auto_start_enabled"
        private const val DEFAULT_DNS = "dns.adguard.com"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(KEY_AUTO_START, false)

        if (!autoStart) {
            Log.d(TAG, "Автозапуск отключён, служба не будет запущена")
            return
        }

        val dnsServer = prefs.getString(KEY_DNS_SERVER, DEFAULT_DNS)!!
        val targetPackages = prefs.getStringSet(KEY_TARGET_PACKAGES, DnsMonitorService.DEFAULT_PACKAGES)!!

        Log.d(TAG, "Загрузка завершена. Запускаем DnsMonitorService (DNS: $dnsServer, Исключения: ${targetPackages.size})")

        val serviceIntent = Intent(context, DnsMonitorService::class.java).apply {
            putExtra(DnsMonitorService.EXTRA_DNS_SERVER, dnsServer)
            putStringArrayListExtra(DnsMonitorService.EXTRA_TARGET_PACKAGES, ArrayList(targetPackages))
        }

        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
    }
}
