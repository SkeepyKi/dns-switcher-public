package com.example.dns_switcher

object Constants {
    const val PREFS_NAME = "dns_switcher_prefs"
    const val KEY_DNS_SERVER = "dns_server"
    const val KEY_TARGET_PACKAGES = "target_packages"
    const val KEY_AUTO_START = "auto_start_enabled"
    const val KEY_SERVICE_RUNNING = "service_running"
    
    const val DEFAULT_DNS = "dns.adguard.com"
    val DEFAULT_PACKAGES = setOf("com.supercell.brawlstars")
}
