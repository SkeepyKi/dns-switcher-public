package com.example.dns_switcher

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings

/**
 * Класс для управления системными настройками Private DNS.
 *
 * Переключает режим DNS через Settings.Global:
 * - private_dns_mode: "hostname" (включить с указанным сервером) / "off" (отключить)
 * - private_dns_specifier: адрес DNS-сервера
 *
 * Требует разрешение WRITE_SECURE_SETTINGS, выданное через ADB.
 */
class DnsController(private val context: Context) {

    companion object {
        private const val PRIVATE_DNS_MODE = "private_dns_mode"
        private const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"

        /** Режим: использовать указанный DNS-сервер */
        private const val MODE_HOSTNAME = "hostname"

        /** Режим: DNS отключён */
        private const val MODE_OFF = "off"
    }

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * Включает Private DNS с указанным адресом сервера.
     * @param dnsServer адрес DNS-сервера (например, "dns.adguard.com")
     * @return true если настройки успешно применены
     */
    fun enableDns(dnsServer: String): Boolean {
        return try {
            Settings.Global.putString(contentResolver, PRIVATE_DNS_SPECIFIER, dnsServer)
            Settings.Global.putString(contentResolver, PRIVATE_DNS_MODE, MODE_HOSTNAME)
            true
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Отключает Private DNS.
     * @return true если настройки успешно применены
     */
    fun disableDns(): Boolean {
        return try {
            Settings.Global.putString(contentResolver, PRIVATE_DNS_MODE, MODE_OFF)
            true
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Возвращает текущий режим Private DNS.
     */
    fun getCurrentMode(): String {
        return Settings.Global.getString(contentResolver, PRIVATE_DNS_MODE) ?: "unknown"
    }

    /**
     * Возвращает текущий адрес DNS-сервера.
     */
    fun getCurrentDnsServer(): String {
        return Settings.Global.getString(contentResolver, PRIVATE_DNS_SPECIFIER) ?: ""
    }
}
