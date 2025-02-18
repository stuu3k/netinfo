package com.ungifted.netinfo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import java.net.NetworkInterface
import java.util.*
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager

class NetworkInfo(private val context: Context) {

    data class MobileDataInfo(
        val networkType: String,
        val operator: String,
        val signalStrength: String
    )

    fun getWifiStatus(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return when (wifiManager.wifiState) {
            WifiManager.WIFI_STATE_ENABLED -> "Connected"
            WifiManager.WIFI_STATE_DISABLED -> "Disabled"
            else -> "Not Available"
        }
    }

    fun getSSID(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo
        return info.ssid.removeSurrounding("\"") // Remove quotes from SSID
    }

    fun getSignalStrength(): Int {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        return wifiManager.connectionInfo.rssi
    }

    fun getMacAddress(): String {
        try {
            Log.d("MAC_DEBUG", "Starting MAC address lookup...")
            
            // Check for required permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    Log.d("MAC_DEBUG", "Location permission not granted")
                    return "Location Permission Required"
                }
            }

            // Method 1: Try using Bluetooth adapter
            try {
                @Suppress("DEPRECATION")
                val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                if (bluetoothAdapter != null) {
                    val address = bluetoothAdapter.address
                    Log.d("MAC_DEBUG", "Bluetooth MAC: $address")
                    if (!address.isNullOrEmpty() && address != "02:00:00:00:00:00") {
                        return address.uppercase()
                    }
                }
            } catch (e: Exception) {
                Log.d("MAC_DEBUG", "Error getting Bluetooth MAC: ${e.message}")
            }

            // Method 2: Try using reflection on Build class
            try {
                val fields = Build::class.java.declaredFields
                for (field in fields) {
                    if (field.name.contains("SERIAL", ignoreCase = true)) {
                        field.isAccessible = true
                        val value = field.get(null) as String?
                        Log.d("MAC_DEBUG", "Serial field ${field.name}: $value")
                        if (!value.isNullOrEmpty() && value != "unknown") {
                            return value.uppercase()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("MAC_DEBUG", "Error getting serial: ${e.message}")
            }

            // Method 3: Try using Settings.Secure
            try {
                val androidId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                Log.d("MAC_DEBUG", "Android ID: $androidId")
                if (!androidId.isNullOrEmpty()) {
                    // Convert Android ID to MAC-like format
                    val macFormat = androidId.chunked(2).take(6).joinToString(":")
                    Log.d("MAC_DEBUG", "Converted Android ID to MAC format: $macFormat")
                    return macFormat.uppercase()
                }
            } catch (e: Exception) {
                Log.d("MAC_DEBUG", "Error getting Android ID: ${e.message}")
            }

            // Method 4: Try WifiManager as last resort
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                val connectionInfo = wifiManager.connectionInfo
                val macAddress = connectionInfo.macAddress
                Log.d("MAC_DEBUG", "WifiManager MAC: $macAddress")
                
                if (!macAddress.isNullOrEmpty() && 
                    macAddress != "02:00:00:00:00:00" && 
                    macAddress != "00:00:00:00:00:00") {
                    return macAddress.uppercase()
                }
            }

        } catch (e: Exception) {
            Log.e("MAC_DEBUG", "Error getting MAC address", e)
            e.printStackTrace()
        }

        Log.d("MAC_DEBUG", "No MAC address found, returning Not Available")
        return "Not Available"
    }

    fun getDnsServer(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val dhcpInfo = wifiManager.dhcpInfo
        return if (dhcpInfo != null) {
            formatIp(dhcpInfo.dns1)
        } else {
            "Not Available"
        }
    }

    fun getDhcpServer(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val dhcpInfo = wifiManager.dhcpInfo
        return if (dhcpInfo != null) {
            formatIp(dhcpInfo.serverAddress)
        } else {
            "Not Available"
        }
    }

    fun getGateway(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val dhcpInfo = wifiManager.dhcpInfo
        return if (dhcpInfo != null) {
            formatIp(dhcpInfo.gateway)
        } else {
            "Not Available"
        }
    }

    fun getDnsSuffix(): String {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val linkProperties = connectivityManager.getLinkProperties(network)
                val domains = linkProperties?.domains
                if (!domains.isNullOrEmpty()) {
                    return domains
                }
            }

            // If not found through ConnectivityManager, try network interfaces
            val networkInterfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in networkInterfaces) {
                if (networkInterface.name.startsWith("wlan")) {
                    val dnsSuffix = networkInterface.displayName
                        .split(".")
                        .drop(1)
                        .joinToString(".")
                    
                    if (dnsSuffix.isNotEmpty()) {
                        return dnsSuffix
                    }
                }
            }

            // If still not found, try DHCP info
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                val dhcpInfo = wifiManager.dhcpInfo
                if (dhcpInfo != null) {
                    val domainName = dhcpInfo.toString()
                        .split("\n")
                        .find { it.contains("domain") }
                        ?.substringAfter("domain")
                        ?.trim()

                    if (!domainName.isNullOrEmpty()) {
                        return domainName
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return "Not Available"
    }

    fun getMobileDataInfo(): MobileDataInfo? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                
                return MobileDataInfo(
                    networkType = when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> {
                            when (telephonyManager.dataNetworkType) {
                                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                                TelephonyManager.NETWORK_TYPE_HSDPA,
                                TelephonyManager.NETWORK_TYPE_HSPA,
                                TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                                else -> "2G"
                            }
                        }
                        else -> "Unknown"
                    },
                    operator = telephonyManager.networkOperatorName ?: "Unknown",
                    signalStrength = "Not Available"
                )
            }
        }
        return null
    }

    fun getIpAddress(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val dhcpInfo = wifiManager.dhcpInfo
        return if (dhcpInfo != null) {
            formatIp(dhcpInfo.ipAddress)
        } else {
            "Not Available"
        }
    }

    fun getSubnetMask(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val dhcpInfo = wifiManager.dhcpInfo
        return if (dhcpInfo != null) {
            formatIp(dhcpInfo.netmask)
        } else {
            "Not Available"
        }
    }

    private fun formatIp(ip: Int): String {
        return if (ip == 0) {
            "Not Available"
        } else {
            "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
        }
    }
} 