package com.example.dns_switcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Quick Settings Tile — переключатель DNS-мониторинга в шторке уведомлений.
 */
class DnsQsTileService : TileService() {

    companion object {
        private const val TAG = "DnsQsTileService"


        fun requestTileUpdate(context: Context) {
            requestListeningState(
                context,
                ComponentName(context, DnsQsTileService::class.java)
            )
        }
    }

    private var statusReceiver: BroadcastReceiver? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()

        statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val isRunning = intent.getBooleanExtra(DnsMonitorService.EXTRA_IS_RUNNING, false)
                val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                prefs.edit().putBoolean(Constants.KEY_SERVICE_RUNNING, isRunning).apply()
                updateTileState()
            }
        }
        val filter = IntentFilter(DnsMonitorService.ACTION_STATUS_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStopListening() {
        statusReceiver?.let {
            unregisterReceiver(it)
            statusReceiver = null
        }
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()

        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val isRunning = prefs.getBoolean(Constants.KEY_SERVICE_RUNNING, false)

        if (isRunning) {
            Log.d(TAG, "Tile: остановка службы")
            stopService(Intent(this, DnsMonitorService::class.java))
            prefs.edit().putBoolean(Constants.KEY_SERVICE_RUNNING, false).apply()
        } else {
            Log.d(TAG, "Tile: запуск службы")
            val dnsServer = prefs.getString(Constants.KEY_DNS_SERVER, Constants.DEFAULT_DNS)!!
            val targetPackages = prefs.getStringSet(Constants.KEY_TARGET_PACKAGES, Constants.DEFAULT_PACKAGES)!!

            val serviceIntent = Intent(this, DnsMonitorService::class.java).apply {
                putExtra(DnsMonitorService.EXTRA_DNS_SERVER, dnsServer)
                putStringArrayListExtra(DnsMonitorService.EXTRA_TARGET_PACKAGES, ArrayList(targetPackages))
            }
            androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
            prefs.edit().putBoolean(Constants.KEY_SERVICE_RUNNING, true).apply()
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        val isRunning = prefs.getBoolean(Constants.KEY_SERVICE_RUNNING, false)

        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_dns)
        tile.label = "DNS Switch"
        tile.subtitle = if (isRunning) "Мониторинг ВКЛ" else "Выключено"
        tile.contentDescription = if (isRunning) "DNS мониторинг активен" else "DNS мониторинг выключен"

        tile.updateTile()
    }
}
