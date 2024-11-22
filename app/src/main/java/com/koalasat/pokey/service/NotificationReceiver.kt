package com.koalasat.pokey.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.MuteEntity
import com.koalasat.pokey.models.NostrClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent !== null) {
            when (intent.action) {
                "MUTE" -> {
                    Log.d("Pokey", "MUTE")
                    val eventId = intent.getStringExtra("rootEventId")
                    if (eventId?.isNotEmpty() == true) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = AppDatabase.getDatabase(context, Pokey.getInstance().getHexKey())
                            if (db.applicationDao().existsMuteEntity(eventId) == 0) {
                                val muteEntity = MuteEntity(id = 0, kind = 10000, tagType = "e", entityId = eventId, private = 0)
                                db.applicationDao().insertMute(muteEntity)
                                NostrClient.publishPublicMute(context)
                            }
                        }
                    }
                }
            }
            val notificationId = intent.getIntExtra("notificationId", -1)
            Log.d("Pokey", "notificationId $notificationId")
            if (notificationId != -1) {
                val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }
        } else {
            Log.d("Pokey", "NotificationReceiver no context provided")
        }
    }
}
