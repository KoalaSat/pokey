package com.koalasat.pokey.service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.koalasat.pokey.Connectivity
import com.koalasat.pokey.MainActivity
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.R
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.NotificationEntity
import com.koalasat.pokey.models.EncryptedStorage
import com.koalasat.pokey.models.ExternalSigner
import com.koalasat.pokey.models.NostrClient
import com.koalasat.pokey.utils.images.CircleTransform
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.LnInvoiceUtil
import com.vitorpamplona.quartz.encoders.toNote
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

class NotificationsService : Service() {
    private var broadcastIntentName = "com.shared.NOSTR"
    private var channelRelaysId = "RelaysConnections"
    private var channelNotificationsId = "Notifications"

    private val timer = Timer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processedEvents = ConcurrentHashMap<String, Boolean>()
    private val authRelays = ConcurrentHashMap<String, Long>()
    private var hexPubKeysList = emptyList<String>()

    private val clientNotificationListener =
        object : Client.Listener {
            override fun onAuth(relay: Relay, challenge: String) {
                Log.d("Pokey", "Relay on Auth: ${relay.url} : $challenge")

                CoroutineScope(Dispatchers.IO).launch {
                    val currentTime = TimeUtils.now()
                    val fiveMinutesInMillis = 5 * 60 * 1000
                    val existingTimestamp = authRelays[relay.url]
                    val hexPub = EncryptedStorage.inboxPubKey.value
                    if (hexPub?.isNotEmpty() == true && (existingTimestamp == null || (currentTime - existingTimestamp > fiveMinutesInMillis))) {
                        ExternalSigner.auth(hexPub, relay.url, challenge) { result ->
                            Log.d("Pokey", "Relay on Auth response: ${relay.url} : ${result.toJson()}")
                            relay.send(result)
                            authRelays.putIfAbsent(relay.url, TimeUtils.now())
                        }
                    }
                }
            }

            override fun onSend(relay: Relay, msg: String, success: Boolean) {
                Log.d("Pokey", "Relay send: ${relay.url} - $msg - Success $success")
            }

            override fun onBeforeSend(relay: Relay, event: EventInterface) {
                Log.d("Pokey", "Relay Before Send: ${relay.url} - ${event.toJson()}")
            }

            override fun onError(error: Error, subscriptionId: String, relay: Relay) {
                Log.d("Pokey", "Relay Error: ${relay.url} - ${error.message}")
            }

            override fun onEvent(
                event: Event,
                subscriptionId: String,
                relay: Relay,
                afterEOSE: Boolean,
            ) {
                if (processedEvents.putIfAbsent(event.id, true) == null) {
                    Log.d("Pokey", "Relay Event: ${relay.url} - $subscriptionId - ${event.toJson()}")

                    val anyUserMention = event.taggedUsers().any { it in hexPubKeysList }

                    if (!anyUserMention) return

                    createNoteNotification(event)

                    if (EncryptedStorage.broadcast.value == true) {
                        val intent = Intent(broadcastIntentName)
                        intent.putExtra("EVENT", event.toJson())
                        sendBroadcast(intent)
                        Log.d("Pokey", "Relay Event: ${relay.url} - $subscriptionId - Broadcast")
                    }
                }
            }

            override fun onNotify(relay: Relay, description: String) {
                Log.d("Pokey", "Relay On Notify: ${relay.url} - $description")
            }

            override fun onRelayStateChange(type: Relay.StateType, relay: Relay, subscriptionId: String?) {
                Log.d("Pokey", "Relay state change: ${relay.url} - $type")
            }

            override fun onSendResponse(
                eventId: String,
                success: Boolean,
                message: String,
                relay: Relay,
            ) {
                Log.d("Pokey", "Relay send response: ${relay.url} - $eventId")
            }
        }

    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            var lastNetwork: Network? = null

            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                if (lastNetwork != null && lastNetwork != network) {
                    scope.launch(Dispatchers.IO) {
                        stopSubscription()
                        delay(1000)
                        startSubscription()
                    }
                }

                lastNetwork = network
            }

            // Network capabilities have changed for the network
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)

                scope.launch(Dispatchers.IO) {
                    Log.d(
                        "ServiceManager NetworkCallback",
                        "onCapabilitiesChanged: ${network.networkHandle} hasMobileData ${Connectivity.isOnMobileData} hasWifi ${Connectivity.isOnWifiData}",
                    )
                    if (Connectivity.updateNetworkCapabilities(networkCapabilities)) {
                        stopSubscription()
                        delay(1000)
                        startSubscription()
                    }
                }
            }
        }

    override fun onBind(intent: Intent): IBinder {
        return null!!
    }

    override fun onCreate() {
        val connectivityManager =
            (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startService()

        return START_STICKY
    }

    override fun onDestroy() {
        timer.cancel()
        stopSubscription()

        try {
            val connectivityManager =
                (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.d("Pokey", "Failed to unregisterNetworkCallback", e)
        }

        super.onDestroy()
    }

    private fun startService() {
        try {
            Log.d("Pokey", "Starting foreground service...")
            startForeground(1, createNotification())
            keepAlive()

            startSubscription()

            val connectivityManager =
                (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e("NotificationsService", "Error in service", e)
            startService()
        }
    }

    private fun startSubscription() {
        if (!Client.isSubscribed(clientNotificationListener)) Client.subscribe(clientNotificationListener)

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(this@NotificationsService, "common")
            val users = db.applicationDao().getUsers()
            hexPubKeysList = users.map { it.hexPub }
            NostrClient.start(this@NotificationsService)
        }
    }

    private fun stopSubscription() {
        Client.unsubscribe(clientNotificationListener)
        NostrClient.stop()
    }

    private fun keepAlive() {
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    NostrClient.checkRelaysHealth(this@NotificationsService)
                }
            },
            5000,
            61000,
        )
    }

    private fun createNotification(): Notification {
        Log.d("Pokey", "Building channels...")
        val channelRelays = NotificationChannel(channelRelaysId, getString(R.string.relays_connection), NotificationManager.IMPORTANCE_DEFAULT)
        channelRelays.setSound(null, null)

        val channelNotification = NotificationChannel(channelNotificationsId, getString(R.string.configuration), NotificationManager.IMPORTANCE_HIGH)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.createNotificationChannel(channelRelays)
        notificationManager.createNotificationChannel(channelNotification)

        Log.d("Pokey", "Building notification...")
        val notificationBuilder =
            NotificationCompat.Builder(this, channelRelaysId)
                .setContentTitle(getString(R.string.pokey_is_running_in_background))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSmallIcon(R.drawable.ic_launcher_foreground)

        return notificationBuilder.build()
    }

    private fun createNoteNotification(event: Event) {
        CoroutineScope(Dispatchers.IO).launch {
            val userHexPub = event.taggedUsers().find { it in hexPubKeysList }
            if (userHexPub?.isNotEmpty() != true || userHexPub == event.pubKey) return@launch

            val db = AppDatabase.getDatabase(this@NotificationsService, "common")
            val existsEvent = db.applicationDao().existsNotification(event.id)
            if (existsEvent > 0) return@launch

            var notificationEntity = NotificationEntity(
                id = 0,
                eventId = event.id,
                accountKexPub = userHexPub,
                time = event.createdAt,
            )
            notificationEntity.id = db.applicationDao().insertNotification(notificationEntity)!!

            if (event.firstTaggedEvent()?.isNotEmpty() == true && db.applicationDao().existsMuteEntity(event.firstTaggedEvent().toString(), userHexPub) == 1) return@launch
            if (!event.hasVerifiedSignature()) return@launch

            val user = db.applicationDao().getUser(userHexPub)

            var title = ""
            var text = ""
            val pubKey = userHexPub
            var nip32Bech32 = ""
            var avatar = ""

            when (event.kind) {
                1 -> {
                    title = when {
                        event.content().contains("nostr:$pubKey") -> {
                            if (user?.notifyMentions != 1) return@launch
                            getString(R.string.new_mention)
                        }
                        event.content().contains("nostr:nevent1") -> {
                            if (user?.notifyQuotes != 1) return@launch
                            getString(R.string.new_quote)
                        }
                        else -> {
                            if (user?.notifyReplies != 1) return@launch
                            getString(R.string.new_reply)
                        }
                    }
                    text = event.content().replace(Regex("nostr:[a-zA-Z0-9]+"), "")
                    nip32Bech32 = Hex.decode(event.id).toNote()
                }
                6 -> {
                    if (user?.notifyReposts != 1) return@launch

                    title = getString(R.string.new_repost)
                    nip32Bech32 = Hex.decode(event.id).toNote()
                }
                4, 1059 -> {
                    if (user?.notifyPrivate != 1) return@launch

                    title = getString(R.string.new_private)
                    nip32Bech32 = Hex.decode(event.pubKey).toNpub()
                }
                7 -> {
                    if (user?.notifyReactions != 1) return@launch

                    title = getString(R.string.new_reaction)
                    text = if (event.content.isEmpty() || event.content == "+") {
                        "♥\uFE0F"
                    } else {
                        event.content
                    }
                    val taggedEvent = event.taggedEvents().first()
                    nip32Bech32 = Hex.decode(taggedEvent).toNote()
                }
                9735 -> {
                    if (user?.notifyZaps != 1) return@launch

                    title = getString(R.string.new_zap)
                    val bolt11 = event.firstTag("bolt11")
                    if (!bolt11.isNullOrEmpty()) {
                        val sats = LnInvoiceUtil.getAmountInSats(bolt11).toInt()
                        text = "⚡ $sats Sats"
                    }
                    try {
                        val description = event.firstTag("description")?.let { JSONObject(it) }
                        if (description != null) {
                            val tags = description.getJSONArray("tags")
                            val eTag = tags.getJSONArray(0)
                            nip32Bech32 = Hex.decode(eTag.getString(1)).toNote()

                            val content = description.getString("content")
                            if (content.isNotEmpty()) text = "$text: $content"
                        }
                    } catch (e: JSONException) {
                        Log.d("Pokey", "Invalid Zap JSON")
                    }
                }
            }

            if (title.isEmpty()) return@launch

            NostrClient.getNip05Content(event.pubKey, onResponse = {
                try {
                    var authorName = it?.getString("name")
                    if (authorName?.isNotEmpty() == true && !title.contains(authorName)) {
                        title += " from $authorName"
                    }
                    avatar = it?.getString("picture").toString()
                } catch (e: JSONException) { }

                notificationEntity.avatarUrl = avatar
                notificationEntity.title = title
                notificationEntity.text = text
                notificationEntity.nip32 = nip32Bech32
                db.applicationDao().updateNotification(notificationEntity)

                if (avatar.isEmpty()) {
                    displayNoteNotification(userHexPub, title, text, nip32Bech32, null, event)
                } else {
                    val handler = Handler(Looper.getMainLooper())
                    handler.post {
                        Picasso.get()
                            .load(avatar)
                            .resize(100, 100)
                            .centerCrop()
                            .transform(CircleTransform())
                            .into(object : Target {
                                override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom) {
                                    displayNoteNotification(userHexPub, title, text, nip32Bech32, bitmap, event)
                                }

                                override fun onBitmapFailed(e: Exception, errorDrawable: Drawable?) {
                                    displayNoteNotification(userHexPub, title, text, nip32Bech32, null, event)
                                }

                                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                                    // Optional: Handle the loading state
                                }
                            })
                    }
                }
            })
        }
    }

    private fun displayNoteNotification(hexPub: String, title: String, text: String, authorBech32: String, avatar: Bitmap?, event: Event) {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val intentAction1 = Intent(this, NotificationReceiver::class.java).apply {
            action = "MUTE"
            putExtra("rootEventId", event.firstTaggedEvent())
            putExtra("notificationId", event.id.hashCode())
            putExtra("hexPub", hexPub)
        }
        val pendingIntentMute = PendingIntent.getBroadcast(this, event.id.hashCode(), intentAction1, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        var builder: NotificationCompat.Builder =
            NotificationCompat.Builder(
                applicationContext,
                channelNotificationsId,
            )
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setLargeIcon(avatar)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(0, getString(R.string.mute_thread), pendingIntentMute)
                .setAutoCancel(true)

        var deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("nostr:$authorBech32")
        }
        if (deepLinkIntent.resolveActivity(this@NotificationsService.packageManager) == null) {
            deepLinkIntent = Intent(this@NotificationsService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this@NotificationsService,
            0,
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        builder = builder.setContentIntent(pendingIntent)

        notificationManager.notify(event.id.hashCode(), builder.build())
        Pokey.updateNewPrivateRelay(event.createdAt)
    }
}
