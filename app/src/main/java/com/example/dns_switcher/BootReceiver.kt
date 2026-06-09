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
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(Constants.KEY_AUTO_START, false)

        if (!autoStart) {
            Log.d(TAG, "Автозапуск отключён, служба не будет запущена")
            return
        }

        val dnsServer = prefs.getString(Constants.KEY_DNS_SERVER, Constants.DEFAULT_DNS)!!
        val targetPackages = prefs.getStringSet(Constants.KEY_TARGET_PACKAGES, Constants.DEFAULT_PACKAGES)!!

        Log.d(TAG, "Загрузка завершена. Запускаем DnsMonitorService (DNS: $dnsServer, Исключения: ${targetPackages.size})")

        val serviceIntent = Intent(context, DnsMonitorService::class.java).apply {
            putExtra(DnsMonitorService.EXTRA_DNS_SERVER, dnsServer)
            putStringArrayListExtra(DnsMonitorService.EXTRA_TARGET_PACKAGES, ArrayList(targetPackages))
        }

        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
    }
}
