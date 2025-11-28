package com.nearnet.sessionlayer.logic

import android.content.Context
import android.util.Log

//const val SERVER_ADDRESS = "95.108.77.201"
//const val SERVER_PORT = 3002


/**
 * Obiekt zarządzający konfiguracją adresu serwera
 */
object ServerConfig {
    private const val TAG = "ServerConfig"
    private const val PREFS_NAME = "ServerPrefs"
    private const val KEY_CUSTOM_ADDRESS = "custom_server_address"

    // Domyślny adres serwera
    const val DEFAULT_ADDRESS = "95.108.77.201"
    const val DEFAULT_PORT = 3002

    private var cachedAddress: String? = null
    private var cachedPort: Int? = null

    /**
     * Inicjalizuja konfiguracji serwera przy starcie aplikacji*
     */
    fun initialize(context: Context) {
        Log.d(TAG, "Inicjalizacja ServerConfig...")
        loadCustomServer(context)
    }

    /**
     * Pobiera aktualny adres serwera
     *
     * @return Adres IP serwera
     */
    fun getServerAddress(context: Context): String {
        if (cachedAddress != null) {
            return cachedAddress!!
        }
        loadCustomServer(context)
        return cachedAddress ?: DEFAULT_ADDRESS
    }

    /**
     * Pobiera aktualny port serwera
     *
     * @return Port serwera
     */
    fun getServerPort(context: Context): Int {
        if (cachedPort != null) {
            return cachedPort!!
        }
        loadCustomServer(context)
        return cachedPort ?: DEFAULT_PORT
    }

    /**
     * Ustawia niestandardowy adres serwera
     *
     * @param address Adres w formacie "192.168.1.100:3002"
     * @return true jesli zapisano pomyślnie, false w przeciwnym razie
     */
    fun setCustomServer(context: Context, address: String): Boolean {
        Log.d(TAG, "Ustawianie niestandardowego serwera: $address")

        val parts = address.split(":")
        if (parts.size != 2) {
            Log.e(TAG, "Niepoprawny format adresu")
            return false
        }

        val ip = parts[0]
        val port = parts[1].toIntOrNull()

        if (port == null) {
            Log.e(TAG, "Niepoprawny port")
            return false
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_ADDRESS, address).commit()

        cachedAddress = ip
        cachedPort = port

        Log.d(TAG, "Niestandardowy serwer zapisany: $ip:$port")
        return true
    }

    /**
     * Przywracanie domyślnego adresu serwera
     */
    fun setDefaultServer(context: Context) {
        Log.d(TAG, "Przywracanie domyślnego serwera")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CUSTOM_ADDRESS).commit()

        cachedAddress = DEFAULT_ADDRESS
        cachedPort = DEFAULT_PORT

        Log.d(TAG, "Przywrócono domyślny serwer: $DEFAULT_ADDRESS:$DEFAULT_PORT")

    }

    /**
     * Sprawdza czy używany jest niestandardowy serwer
     *
     * @return true jeśli używany jest niestandardowy serwer
     */
    fun isUsingCustomServer(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_CUSTOM_ADDRESS)
    }

    /**
     * Pobiera pełny URL serwera
     *
     * @return URL w formacie "https://IP:PORT"
     */
    fun getBaseUrl(context: Context): String {
        val address = getServerAddress(context)
        val port = getServerPort(context)
        return "https://$address:$port"
    }

    /**
     * Ładuje niestandardowy serwer z SharedPreferences
     */
    private fun loadCustomServer(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customAddress = prefs.getString(KEY_CUSTOM_ADDRESS, null)

        if (customAddress != null) {
            val parts = customAddress.split(":")
            if (parts.size == 2) {
                cachedAddress = parts[0]
                cachedPort = parts[1].toIntOrNull() ?: DEFAULT_PORT
                Log.d(TAG, "Załadowano niestandardowy serwer: $cachedAddress:$cachedPort")
            } else {
                Log.w(TAG, "Niepoprawny format zapisanego adresu, używam domyślnego")
                cachedAddress = DEFAULT_ADDRESS
                cachedPort = DEFAULT_PORT
            }
        } else {
            cachedAddress = DEFAULT_ADDRESS
            cachedPort = DEFAULT_PORT
            Log.d(TAG, "Używam domyślnego serwera: $DEFAULT_ADDRESS:$DEFAULT_PORT")
        }
    }

    /**
     * Czysci cache
     */
    fun clearCache() {
        cachedAddress = null
        cachedPort = null
    }
}