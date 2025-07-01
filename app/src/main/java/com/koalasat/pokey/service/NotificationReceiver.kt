package com.koalasat.pokey.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import com.koalasat.pokey.MainActivity

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent !== null) {
            val notificationId = intent.getIntExtra("notificationId", -1)
            if (notificationId != -1) {
                val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }
            when (intent.action) {
                "REFRESH" -> {
                    Log.d("Pokey", "REFRESH action received")

                    val popupIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                    }

                    context.startActivity(popupIntent)
                }
            }
        } else {
            Log.d("Pokey", "NotificationReceiver no context provided")
        }
    }
}
