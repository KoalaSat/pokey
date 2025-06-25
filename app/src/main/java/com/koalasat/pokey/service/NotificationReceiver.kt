package com.koalasat.pokey.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent !== null) {
            when (intent.action) {
                "MUTE_THREAD" -> {
//                    val eventId = intent.getStringExtra("rootEventId")
//                    val hexPub = intent.getStringExtra("hexPub")
//                    Log.d("Pokey", "MUTE_THREAD rootEventId: $eventId")
//                    Log.d("Pokey", "MUTE_THREAD hexPub: $hexPub")
//                    if (eventId?.isNotEmpty() == true && hexPub?.isNotEmpty() == true) {
//                        CoroutineScope(Dispatchers.IO).launch {
//                            val db = AppDatabase.getDatabase(context, "common")
//                            if (db.applicationDao().existsMuteEntity(eventId, hexPub) == 0) {
//                                val muteEntity = MuteEntity(id = 0, kind = 10000, tagType = "e", entityId = eventId, private = 0, hexPub = hexPub)
//                                db.applicationDao().insertMute(muteEntity)
//                                Log.d("Pokey", "Muting event $eventId")
//                                NostrClient.publishMuteList(context)
//                            }
//                        }
//                    }
                }
            }
            val notificationId = intent.getIntExtra("notificationId", -1)
            if (notificationId != -1) {
                val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }
        } else {
            Log.d("Pokey", "NotificationReceiver no context provided")
        }
    }
}
