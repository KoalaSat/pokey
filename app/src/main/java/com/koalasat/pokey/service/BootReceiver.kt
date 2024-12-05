package com.koalasat.pokey.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.models.EncryptedStorage

class BootReceiver : BroadcastReceiver() {
    private val handler = Handler(Looper.getMainLooper())
    private var retryDelayMillis = 1000L

    override fun onReceive(context: Context, intent: Intent) {
        if (!Pokey.isForegroundServiceEnabled(context)) return

        if (intent.action == Intent.ACTION_PACKAGE_REPLACED && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (intent.dataString?.contains("com.koalasat.pokey") == true && Pokey.isForegroundServiceEnabled(context)) {
                Log.d("BootReceiver", "Starting ConnectivityService ACTION_PACKAGE_REPLACED")
                Pokey.getInstance().startService()
            }
        }

        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("BootReceiver", "Starting ConnectivityService ACTION_MY_PACKAGE_REPLACED")
            if (Pokey.isForegroundServiceEnabled(context)) {
                Pokey.getInstance().startService()
            }
        }

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Starting ConnectivityService ACTION_BOOT_COMPLETED")
            if (isConnected(context)) {
                Log.d("ConnectivityReceiver", "Device is connected to the internet")
                if (Pokey.isForegroundServiceEnabled(context)) {
                    EncryptedStorage.init(context)
                    Pokey.getInstance().startService()
                }
            } else {
                retryDelayMillis += retryDelayMillis
                Log.d("ConnectivityReceiver", "Retrying in $retryDelayMillis seconds...")
                handler.postDelayed({
                    onReceive(context, intent)
                }, retryDelayMillis)
            }
        }
    }

    private fun isConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null && networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
