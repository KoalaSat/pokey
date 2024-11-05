package com.koalasat.pokey.service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.koalasat.pokey.Connectivity
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.R
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.NotificationEntity
import com.koalasat.pokey.database.RelayEntity
import com.koalasat.pokey.models.EncryptedStorage
import com.koalasat.pokey.models.ExternalSigner
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.LnInvoiceUtil
import com.vitorpamplona.quartz.encoders.toNote
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import java.time.Instant
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

    private var subscriptionNotificationId = "subscriptionNotificationId"
    private var subscriptionInboxId = "inboxRelays"
    private var subscriptionReadId = "readRelays"

    private val timer = Timer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processedEvents = ConcurrentHashMap<String, Boolean>()

    private val clientListener =
        object : Client.Listener {
            override fun onAuth(relay: Relay, challenge: String) {
                Log.d("Pokey", "Relay on Auth: ${relay.url} : $challenge")
                ExternalSigner.auth(relay.url, challenge) { result ->
                    Log.d("Pokey", "Relay on Auth response: ${relay.url} : ${result.toJson()}")
                    relay.send(result)
                    relay.renewFilters()
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

                    val hexKey = Pokey.getInstance().getHexKey()
                    if (event.pubKey == hexKey || !event.taggedUsers().contains(hexKey)) return

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
                        connectRelays()
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
                        connectRelays()
                        startSubscription()
                    }
                }
            }
        }

    private var defaultRelayUrls = listOf(
        "wss://relay.damus.io",
        "wss://offchain.pub",
        "wss://relay.snort.social",
        "wss://nos.lol",
        "wss://relay.nsec.app",
        "wss://relay.0xchat.com",
    )

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
        Log.d("Pokey", "Starting foreground service...")
        RelayPool.getAll().forEach { RelayPool.removeRelay(it) }
        startForeground(1, createNotification())
        keepAlive()

        val connectivityManager =
            (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        return START_STICKY
    }

    override fun onDestroy() {
        timer.cancel()
        RelayPool.getAll().forEach { RelayPool.removeRelay(it) }

        try {
            val connectivityManager =
                (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.d("Pokey", "Failed to unregisterNetworkCallback", e)
        }

        super.onDestroy()
    }

    private fun startSubscription() {
        val hexKey = Pokey.getInstance().getHexKey()
        if (hexKey.isEmpty()) return

        if (!Client.isSubscribed(clientListener)) Client.subscribe(clientListener)

        val dao = AppDatabase.getDatabase(this@NotificationsService, hexKey).applicationDao()
        var latestNotification = dao.getLatestNotification()
        if (latestNotification == null) latestNotification = Instant.now().toEpochMilli() / 1000

        Client.sendFilter(
            subscriptionNotificationId,
            listOf(
                TypedFilter(
                    types = COMMON_FEED_TYPES,
                    filter = SincePerRelayFilter(
                        kinds = listOf(1, 3, 4, 6, 7, 1059, 9735),
                        tags = mapOf("p" to listOf(hexKey)),
                        since = RelayPool.getAll().associate { it.url to EOSETime(latestNotification) },
                    ),
                ),
            ),
        )
        Client.sendFilterAndStopOnFirstResponse(
            subscriptionReadId,
            listOf(
                TypedFilter(
                    types = EVENT_FINDER_TYPES,
                    filter = SincePerRelayFilter(
                        kinds = listOf(10002),
                        authors = listOf(hexKey),
                    ),
                ),
            ),
            onResponse = { manageInboxRelays(it) },
        )
        Client.sendFilterAndStopOnFirstResponse(
            subscriptionInboxId,
            listOf(
                TypedFilter(
                    types = EVENT_FINDER_TYPES,
                    filter = SincePerRelayFilter(
                        kinds = listOf(10050),
                        authors = listOf(hexKey),
                    ),
                ),
            ),
            onResponse = { manageInboxRelays(it) },
        )
    }

    private fun stopSubscription() {
        Client.unsubscribe(clientListener)
        RelayPool.unloadRelays()
    }

    private fun keepAlive() {
        timer.schedule(
            object : TimerTask() {
                override fun run() {
                    if (RelayPool.getAll().isEmpty()) {
                        connectRelays()
                        startSubscription()
                    }
                    RelayPool.getAll().forEach {
                        if (!it.isConnected()) {
                            Log.d(
                                "Pokey",
                                "Relay ${it.url} is not connected, reconnecting...",
                            )
                            it.connectAndSendFiltersIfDisconnected()
                        }
                    }
                }
            },
            3000,
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

    private fun manageInboxRelays(event: Event) {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(this@NotificationsService, Pokey.getInstance().getHexKey()).applicationDao()
            val lastCreatedRelayAt = dao.getLatestRelaysByKind(event.kind)

            if (lastCreatedRelayAt == null || lastCreatedRelayAt < event.createdAt) {
                RelayPool.unloadRelays()
                dao.deleteRelaysByKind(event.kind)
                event.tags
                    .filter { it.size > 1 && (it[0] == "relay" || it[0] == "r") }
                    .forEach {
                        var read = 1
                        var write = 1
                        if (event.kind == 10002 && it.size > 2) {
                            read = if (it[2] == "read") 1 else 0
                            write = if (it[2] == "write") 1 else 0
                        }
                        val entity = RelayEntity(id = 0, url = it[1], kind = event.kind, createdAt = event.createdAt, read = read, write = write)
                        dao.insertRelay(entity)
                    }

                connectRelays()
                startSubscription()
            }

            if (event.kind == 10050) {
                Pokey.updateLoadingPrivateRelays(false)
            } else {
                Pokey.updateLoadingPublicRelays(false)
            }
        }
    }

    private fun createNoteNotification(event: Event) {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(this@NotificationsService, Pokey.getInstance().getHexKey()).applicationDao()
            val existsEvent = dao.existsNotification(event.id)
            if (existsEvent > 0) return@launch

            if (!event.hasVerifiedSignature()) return@launch

            dao.insertNotification(NotificationEntity(0, event.id, event.createdAt))

            var title = ""
            var text = ""
            val pubKey = EncryptedStorage.pubKey
            var nip32Bech32 = ""

            when (event.kind) {
                1 -> {
                    title = when {
                        event.content().contains("nostr:$pubKey") -> {
                            if (!EncryptedStorage.notifyMentions.value!!) return@launch
                            getString(R.string.new_mention)
                        }
                        event.content().contains("nostr:nevent1") -> {
                            if (!EncryptedStorage.notifyQuotes.value!!) return@launch
                            getString(R.string.new_quote)
                        }
                        else -> {
                            if (!EncryptedStorage.notifyReplies.value!!) return@launch
                            getString(R.string.new_reply)
                        }
                    }
                    text = event.content().replace(Regex("nostr:[a-zA-Z0-9]+"), "")
                    nip32Bech32 = Hex.decode(event.id).toNote()
                }
                6 -> {
                    if (!EncryptedStorage.notifyResposts.value!!) return@launch

                    title = getString(R.string.new_repost)
                    nip32Bech32 = Hex.decode(event.id).toNote()
                }
                4, 1059 -> {
                    if (!EncryptedStorage.notifyPrivate.value!!) return@launch

                    title = getString(R.string.new_private)
                    nip32Bech32 = Hex.decode(event.pubKey).toNpub()
                }
                7 -> {
                    if (!EncryptedStorage.notifyReactions.value!!) return@launch

                    title = getString(R.string.new_reaction)
                    text = if (event.content.isEmpty() || event.content == "+") {
                        "❤\uFE0F"
                    } else {
                        event.content
                    }
                    val taggedEvent = event.taggedEvents().first()
                    nip32Bech32 = Hex.decode(taggedEvent).toNote()
                }
                9735 -> {
                    if (!EncryptedStorage.notifyZaps.value!!) return@launch

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
                3 -> {
                    if (!EncryptedStorage.notifyFollows.value!!) return@launch
                    if (event.taggedUsers().last() != EncryptedStorage.pubKey.value) return@launch

                    title = getString(R.string.new_follow)
                    nip32Bech32 = Hex.decode(event.pubKey).toNpub()
                }
            }

            if (title.isEmpty()) return@launch

            displayNoteNotification(title, text, nip32Bech32, event)
        }
    }

    private fun displayNoteNotification(title: String, text: String, nip32Bech32: String, event: Event) {
        val deepLinkIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("nostr:$nip32Bech32")
        }
        val pendingIntent = PendingIntent.getActivity(
            this@NotificationsService,
            0,
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(
                applicationContext,
                channelNotificationsId,
            )
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        notificationManager.notify(event.hashCode(), builder.build())
    }

    private fun connectRelays() {
        val dao = AppDatabase.getDatabase(this@NotificationsService, Pokey.getInstance().getHexKey()).applicationDao()
        var relays = dao.getReadRelays()
        if (relays.isEmpty()) {
            relays = defaultRelayUrls.map { RelayEntity(id = 0, url = it, kind = 0, createdAt = 0, read = 1, write = 1) }
        }

        relays.forEach {
            Client.sendFilterOnlyIfDisconnected()
            if (RelayPool.getRelays(it.url).isEmpty()) {
                RelayPool.addRelay(
                    Relay(
                        it.url,
                        read = true,
                        write = false,
                        forceProxy = false,
                        activeTypes = COMMON_FEED_TYPES,
                    ),
                )
            }
        }
    }
}
