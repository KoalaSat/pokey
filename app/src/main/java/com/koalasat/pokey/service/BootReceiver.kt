package com.koalasat.pokey.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.koalasat.pokey.Pokey

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Pokey.isForegroundServiceEnabled(context)) return

        if (intent.action == Intent.ACTION_PACKAGE_REPLACED && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (intent.dataString?.contains("com.greenart7c3.nostrsigner") == true) {
                Log.d("BootReceiver", "Starting ConnectivityService ACTION_PACKAGE_REPLACED")
                context.startForegroundService(
                    Intent(
                        context,
                        NotificationsService::class.java,
                    ),
                )
            }
        }

        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("BootReceiver", "Starting ConnectivityService ACTION_MY_PACKAGE_REPLACED")
            context.startForegroundService(
                Intent(
                    context,
                    NotificationsService::class.java,
                ),
            )
        }

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Starting ConnectivityService ACTION_BOOT_COMPLETED")
            context.startForegroundService(
                Intent(
                    context,
                    NotificationsService::class.java,
                ),
            )
        }
    }
}
