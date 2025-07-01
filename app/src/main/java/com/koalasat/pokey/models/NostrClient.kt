package com.koalasat.pokey.models

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.koalasat.pokey.MainActivity
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.R
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.MuteEntity
import com.koalasat.pokey.database.NotificationEntity
import com.koalasat.pokey.database.RelayEntity
import com.koalasat.pokey.database.UserEntity
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.Nip19Bech32.uriToRoute
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.MuteListEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object NostrClient {
    private var subscriptionSubscriptionId = "pokeySubscriptionId"
    private var subscriptionNotificationId = "pokeyNotificationId"
    private var subscriptionPrivateMessagId = "pokeyPrivateMessage"
    private var subscriptionLists = "pokeyLists"
    private var subscriptionMetaId = "pokeyMeta"
    private var subscriptionMuteId = "pokeyMute"

    private var usersNip05 = ConcurrentHashMap<String, JSONObject>()

    private var defaultRelayUrls = listOf(
        "wss://relay.damus.io",
        "wss://offchain.pub",
        "wss://relay.snort.social",
        "wss://nos.lol",
        "wss://relay.nsec.app",
        "wss://relay.0xchat.com",
    )
    fun init() {
        RelayPool.register(Client)
    }

    fun stop() {
        RelayPool.unloadRelays()
    }

    fun start(context: Context) {
        connectRelays(context)
        getLists(context)
        subscribeToInbox(context)
    }

    fun checkRelaysHealth(context: Context) {
        if (RelayPool.getAll().isEmpty()) {
            stop()
            start(context)
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

    fun getRelay(url: String): Relay? {
        return RelayPool.getRelay(url)
    }

    fun deleteRelay(hexPubKey: String, context: Context, url: String, kind: Int) {
        val db = AppDatabase.getDatabase(context, "common")
        db.applicationDao().deleteRelayByUrl(url, kind, hexPubKey)
        val relayStillExists = db.applicationDao().getReadRelays(hexPubKey).any { it.url == url }
        val relay = RelayPool.getRelay(url)
        if (!relayStillExists && relay != null) {
            RelayPool.removeRelay(relay)
        }
    }

    fun addRelay(hexPubKey: String, context: Context, url: String, kind: Int) {
        val db = AppDatabase.getDatabase(context, "common")
        val existsRelay = db.applicationDao().existsRelay(url, kind, hexPubKey)

        if (existsRelay < 1) {
            val entity = RelayEntity(id = 0, url, kind = kind, createdAt = 0, read = 1, write = 1, hexPub = hexPubKey)
            db.applicationDao().insertRelay(entity)

            val relay = RelayPool.getRelay(url)
            if (Pokey.isEnabled.value == true && relay == null) {
                RelayPool.addRelay(
                    Relay(
                        entity.url,
                        read = entity.read == 1,
                        write = entity.write == 1,
                        forceProxy = false,
                        activeTypes = COMMON_FEED_TYPES,
                    ),
                )
                getLists(context)
                subscribeToInbox(context)
            }
        }
    }

    private fun subscribeToInbox(context: Context) {
        val db = AppDatabase.getDatabase(context, "common")
        var latestNotification = db.applicationDao().getLatestNotification()
        if (latestNotification == null) latestNotification = Instant.now().toEpochMilli() / 1000

        val users = db.applicationDao().getUsers()
        val authors = users.map { it.hexPub }

        if (authors.isNotEmpty()) {
            Client.sendFilter(
                subscriptionNotificationId,
                listOf(
                    TypedFilter(
                        types = COMMON_FEED_TYPES,
                        filter = SincePerRelayFilter(
                            kinds = listOf(1, 4, 6, 7, 9735),
                            tags = mapOf("p" to authors),
                            since = RelayPool.getAll().associate { it.url to EOSETime(latestNotification) },
                        ),
                    ),
                ),
            )

            Client.sendFilter(
                subscriptionPrivateMessagId,
                listOf(
                    TypedFilter(
                        types = COMMON_FEED_TYPES,
                        filter = SincePerRelayFilter(
                            kinds = listOf(1059),
                            tags = mapOf("p" to authors),
                            since = RelayPool.getAll().associate { it.url to EOSETime(latestNotification - (2 * 24 * 60 * 60)) },
                        ),
                    ),
                ),
            )
        }

        var subscription = EncryptedStorage.inboxSubscription.value

        if (subscription?.isNotEmpty() == true) {
            val (type, result) = when {
                subscription.startsWith("npub", ignoreCase = true) -> parseBech32(subscription)
                subscription.startsWith("nevent", ignoreCase = true) -> parseBech32(subscription)
                subscription.startsWith("#") -> Pair("hashtag", subscription.replace("#", ""))
                else -> Pair(null, null)
            }

            if (type != null && result != null) {
                val tags = when (type) {
                    "nevent" -> mapOf(Pair("e", listOf(result)))
                    "hashtag" -> mapOf(Pair("t", listOf(result)))
                    else -> null
                }

                var authors = when (type) {
                    "npub" -> listOf(result)
                    else -> null
                }

                if (tags != null || authors != null) {
                    Client.sendFilter(
                        subscriptionSubscriptionId,
                        listOf(
                            TypedFilter(
                                types = COMMON_FEED_TYPES,
                                filter = SincePerRelayFilter(
                                    kinds = listOf(1),
                                    authors = authors,
                                    tags = tags,
                                    since = RelayPool.getAll().associate { it.url to EOSETime(latestNotification) },
                                ),
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun reconnectInbox(hexPubKey: String, context: Context, kind: Int) {
        val db = AppDatabase.getDatabase(context, "common")
        db.applicationDao().deleteRelaysByKind(kind, hexPubKey)
        Client.close(subscriptionLists)
        getLists(context)
    }

    fun publishPrivateRelays(hexPubKey: String, context: Context) {
        val kind = 10050

        val db = AppDatabase.getDatabase(context, "common")
        val privateList = db.applicationDao().getRelaysByKind(kind, hexPubKey)

        val pubKey = hexPubKey
        val createdAt = TimeUtils.now()
        val content = ""
        val tags = privateList.map { arrayOf("relay", it.url) }.toTypedArray()
        val id = Event.generateId(pubKey, createdAt, kind, tags, content).toHexKey()
        val event =
            Event(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = "",
            )
        ExternalSigner.sign(event) {
            val signeEvent = Event(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = it,
            )
            Log.d("Pokey", "Relay private list : ${signeEvent.toJson()}")
            Client.send(signeEvent)
        }
    }

    fun publishPublicRelays(hexPubKey: String, context: Context) {
        val kind = 10002

        val db = AppDatabase.getDatabase(context, "common")
        val publicList = db.applicationDao().getRelaysByKind(kind, hexPubKey)

        val pubKey = hexPubKey
        val createdAt = TimeUtils.now()
        val content = ""
        val tags = publicList.map {
            var tag = arrayOf("r", it.url)
            if (it.read == 1 && it.write == 0) {
                tag += "read"
            } else if (it.read == 0 && it.write == 1) {
                tag += "write"
            }
            tag
        }.toTypedArray()
        val id = Event.generateId(pubKey, createdAt, kind, tags, content).toHexKey()
        val event =
            Event(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = "",
            )
        ExternalSigner.sign(event) {
            val signeEvent = Event(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = it,
            )
            Log.d("Pokey", "Relay public list : ${signeEvent.toJson()}")
            Client.send(signeEvent)
        }
    }

    fun getNip05Content(hexPubKey: String, context: Context, onResponse: (JSONObject?) -> Unit) {
        if (usersNip05.containsKey(hexPubKey)) {
            onResponse(usersNip05.getValue(hexPubKey))
        } else {
            val handler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context, "common")
                    val user = db.applicationDao().getUser(hexPubKey)

                    if (user != null) {
                        val content = JSONObject()
                        content.put("name", user.name)
                        content.put("avatar", user.avatar)
                        content.put("createdAt", user.createdAt)
                        onResponse(content)
                    } else {
                        onResponse(null)
                    }
                }
            }
            handler.postDelayed(timeoutRunnable, 5000)

            Client.sendFilterAndStopOnFirstResponse(
                subscriptionMetaId + hexPubKey.substring(0, 6),
                listOf(
                    TypedFilter(
                        types = EVENT_FINDER_TYPES,
                        filter = SincePerRelayFilter(
                            kinds = listOf(0),
                            authors = listOf(hexPubKey),
                        ),
                    ),
                ),
                onResponse = { event ->
                    if (event.pubKey == hexPubKey) {
                        handler.removeCallbacks(timeoutRunnable)
                        if (event.content.isNotEmpty()) {
                            try {
                                val content = JSONObject(event.content)
                                content.put("created_at", event.createdAt)
                                usersNip05.put(event.pubKey, content)
                                onResponse(content)
                            } catch (e: JSONException) {
                                Log.d("Pokey", "Invalid NIP05 JSON: $e")
                                usersNip05.put(event.pubKey, JSONObject())
                                onResponse(null)
                            }
                        }
                    }
                },
            )
        }
    }

    fun parseNpub(value: String): String? {
        if (value.isEmpty()) return null

        val parseReturn = uriToRoute(value)

        when (val parsed = parseReturn?.entity) {
            is Nip19Bech32.NPub -> {
                return parsed.hex
            }
        }

        return null
    }

    fun parseBech32(value: String?): Pair<String?, String?> {
        if (value?.isEmpty() == true) return Pair(null, null)

        val parseReturn = uriToRoute(value)

        when (val parsed = parseReturn?.entity) {
            is Nip19Bech32.NPub -> {
                return Pair("npub", parsed.hex)
            }
            is Nip19Bech32.NEvent -> {
                return Pair("nevent", parsed.hex)
            }
        }

        return Pair(null, null)
    }

    private fun getLists(context: Context) {
        val db = AppDatabase.getDatabase(context, "common")
        val users = db.applicationDao().getUsers()
        val authors = users.map { it.hexPub }

        if (authors.isNotEmpty()) {
            Client.sendFilter(
                subscriptionLists,
                listOf(
                    TypedFilter(
                        types = COMMON_FEED_TYPES,
                        filter = SincePerRelayFilter(
                            kinds = listOf(10000, 10002, 10050),
                            authors = authors,
                        ),
                    ),
                ),
            )
        }
    }

    private fun connectRelays(context: Context) {
        val hexPubKey = EncryptedStorage.inboxPubKey.value.toString()
        val db = AppDatabase.getDatabase(context, "common")
        var relays = db.applicationDao().getReadRelays(hexPubKey)
        if (relays.isEmpty()) {
            relays = defaultRelayUrls.map { RelayEntity(id = 0, url = it, kind = 0, createdAt = 0, read = 1, write = 1, hexPub = "") }
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

    fun manageInboxRelays(context: Context, event: Event) {
        val db = AppDatabase.getDatabase(context, "common")
        val lastCreatedRelayAt = db.applicationDao().getLatestRelaysByKind(event.kind, event.pubKey)

        if (lastCreatedRelayAt == null || lastCreatedRelayAt < event.createdAt) {
            db.applicationDao().getRelaysByKind(event.kind, event.pubKey).forEach {
                deleteRelay(event.pubKey, context, it.url, it.kind)
            }
            event.tags
                .filter { it.size > 1 && (it[0] == "relay" || it[0] == "r") }
                .forEach {
                    var read = 1
                    var write = 1
                    if (event.kind == 10002 && it.size > 2) {
                        read = if (it[2] == "read") 1 else 0
                        write = if (it[2] == "write") 1 else 0
                    }
                    val entity = RelayEntity(id = 0, url = it[1], kind = event.kind, createdAt = event.createdAt, read = read, write = write, hexPub = event.pubKey)
                    db.applicationDao().insertRelay(entity)
                }
            connectRelays(context)
            subscribeToInbox(context)
        }

        if (event.kind == 10050) {
            Pokey.updateLoadingPrivateRelays(false)
        } else {
            Pokey.updateLoadingPublicRelays(false)
        }
    }

    fun manageMuteList(context: Context, event: MuteListEvent) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context, "common")
            val lastCreatedAt = db.applicationDao().getMostRecentMuteListDate(event.pubKey) ?: 0

            if (event.createdAt > lastCreatedAt) {
                if (Pokey.appHasFocus.value == true) {
                    db.applicationDao().deleteMuteList(event.kind, event.pubKey)
                    db.applicationDao().clearMutedNotifications(event.pubKey)

                    val user = db.applicationDao().getUser(event.pubKey)
                    if (user?.signer == 1 && event.content != "") {
                        val intent = context.packageManager.getLaunchIntentForPackage(ExternalSigner.EXTERNAL_SIGNER)
                        if (intent != null) {
                            ExternalSigner.decrypt(event) {
                                try {
                                    val privateTags = JSONArray(it)
                                    Log.d("Pokey", "Private mute list : ${privateTags.length()}")
                                    CoroutineScope(Dispatchers.IO).launch {
                                        for (i in 0 until privateTags.length()) {
                                            val tag = privateTags.getJSONArray(i)
                                            if (tag.length() > 1) {
                                                val muteEntity = MuteEntity(id = 0, kind = event.kind, tagType = tag.getString(0), entityId = tag.getString(1), private = 1, hexPub = event.pubKey, createdAt = event.createdAt)
                                                db.applicationDao().insertMute(muteEntity)
                                                if (muteEntity.tagType == "e") {
                                                    db.applicationDao().updateMuteThreadNotifications(muteEntity.entityId, 1)
                                                } else if (muteEntity.tagType == "p") {
                                                    db.applicationDao().updateMuteUserNotifications(muteEntity.entityId, 1)
                                                }
                                            }
                                        }
                                        val handler = Handler(Looper.getMainLooper())
                                        handler.post {
                                            Toast.makeText(context, context.getString(R.string.private_mute_updated, privateTags.length()), Toast.LENGTH_LONG).show()
                                        }
                                        Pokey.updateLoadingMuteList(false)
                                    }
                                } catch (e: JSONException) {
                                    val handler = Handler(Looper.getMainLooper())
                                    handler.post {
                                        Toast.makeText(context, context.getString(R.string.invalid_private_mute), Toast.LENGTH_LONG).show()
                                    }
                                    Pokey.updateLoadingMuteList(false)
                                }
                            }
                        } else {
                            val handler = Handler(Looper.getMainLooper())
                            handler.post {
                                Toast.makeText(context, context.getString(R.string.external_signer_not_found), Toast.LENGTH_LONG).show()
                            }
                            Pokey.updateLoadingMuteList(false)
                        }
                    }

                    Log.d("Pokey", "Public mute list : ${event.tags.size}")
                    CoroutineScope(Dispatchers.IO).launch {
                        var muteEntities = emptyList<MuteEntity>()
                        event.tags.forEach {
                            val newEntity = MuteEntity(id = 0, kind = event.kind, tagType = it[0], entityId = it[1], private = 0, hexPub = event.pubKey, createdAt = event.createdAt)
                            muteEntities = muteEntities.plus(newEntity)
                            if (newEntity.tagType == "e") {
                                db.applicationDao().updateMuteThreadNotifications(newEntity.entityId, 1)
                            } else if (newEntity.tagType == "p") {
                                db.applicationDao().updateMuteUserNotifications(newEntity.entityId, 1)
                            }
                        }
                        db.applicationDao().insertAll(muteEntities)
                        Pokey.updateLoadingMuteList(false)
                    }
                } else {
                    notifyNewPrivateList(context)
                }
            } else {
                Pokey.updateLoadingMuteList(false)
            }
        }
    }

    fun publishMuteUser(context: Context, event: NotificationEntity) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context, "common")
            val user = db.applicationDao().getUser(event.accountKexPub)
            if (user !== null) {
                val signerHexPubKey = user.hexPub
                val lastCreatedAt = db.applicationDao().getMostRecentMuteListDate(signerHexPubKey) ?: 0
                val muteEntity = MuteEntity(id = 0, kind = 10000, tagType = "p", entityId = event.pubKey, private = 1, hexPub = signerHexPubKey, createdAt = lastCreatedAt)
                db.applicationDao().insertMute(muteEntity)
                db.applicationDao().updateMuteUserNotifications(event.pubKey, 1)
                publishMuteList(context, user)
            }
        }
    }

    fun publishMuteThread(context: Context, event: NotificationEntity) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context, "common")
            val user = db.applicationDao().getUser(event.accountKexPub)
            if (user !== null) {
                val signerHexPubKey = user.hexPub
                val lastCreatedAt = db.applicationDao().getMostRecentMuteListDate(user.hexPub) ?: 0
                val rootId = if (event.rootId == "") event.eventId else event.rootId
                val muteEntity = MuteEntity(id = 0, kind = 10000, tagType = "e", entityId = rootId, private = 0, hexPub = signerHexPubKey, createdAt = lastCreatedAt)
                db.applicationDao().insertMute(muteEntity)
                db.applicationDao().updateMuteThreadNotifications(rootId, 1)
                publishMuteList(context, user)
            }
        }
    }

    fun publishMuteList(context: Context, user: UserEntity) {
        val kind = 10000

        if (user.signer == 1) {
            val signerPubKey = user.hexPub
            val db = AppDatabase.getDatabase(context, "common")
            val lastCreatedAt = db.applicationDao().getMostRecentMuteListDate(signerPubKey) ?: 0
            val muteList = db.applicationDao().getMuteList(kind, signerPubKey, lastCreatedAt)

            var publicMuteList = emptyArray<Array<String>>()
            var privateMuteList = emptyArray<Array<String>>()

            for (entity in muteList) {
                if (entity.private == 1) {
                    privateMuteList = privateMuteList.plus(arrayOf(entity.tagType, entity.entityId))
                } else {
                    publicMuteList = publicMuteList.plus(arrayOf(entity.tagType, entity.entityId))
                }
            }

            val pubKey = signerPubKey
            val createdAt = TimeUtils.now()
            ExternalSigner.encrypt(JSONArray(privateMuteList).toString(), signerPubKey) { content ->
                val id = Event.generateId(pubKey, createdAt, kind, publicMuteList, content).toHexKey()
                val event =
                    Event(
                        id = id,
                        pubKey = pubKey,
                        createdAt = createdAt,
                        kind = kind,
                        tags = publicMuteList,
                        content = content,
                        sig = "",
                    )
                ExternalSigner.sign(event) {
                    val signeEvent = Event(
                        id = id,
                        pubKey = pubKey,
                        createdAt = createdAt,
                        kind = kind,
                        tags = publicMuteList,
                        content = content,
                        sig = it,
                    )
                    Log.d("Pokey", "Mute public list : ${signeEvent.toJson()}")
                    Client.send(signeEvent)
                }
            }
        }
    }

    fun fetchMuteList(context: Context, hexPubKey: String) {
        Client.sendFilterAndStopOnFirstResponse(
            subscriptionMuteId + hexPubKey.substring(0, 6),
            listOf(
                TypedFilter(
                    types = EVENT_FINDER_TYPES,
                    filter = SincePerRelayFilter(
                        kinds = listOf(10000),
                        authors = listOf(hexPubKey),
                    ),
                ),
            ),
            onResponse = { event ->
                manageMuteList(context, event as MuteListEvent)
            },
        )
    }

    fun noteIsSubscription(event: Event): Boolean {
        var subscription = EncryptedStorage.inboxSubscription.value

        if (subscription?.isNotEmpty() == true) {
            val (type, result) = when {
                subscription.startsWith("npub", ignoreCase = true) -> parseBech32(subscription)
                subscription.startsWith("nevent", ignoreCase = true) -> parseBech32(subscription)
                subscription.startsWith("#") -> Pair("hashtag", subscription.replace("#", ""))
                else -> Pair(null, null)
            }
            return when (type) {
                "npub" -> event.pubKey == result
                "nevent" -> event.taggedEvents().contains(result)
                "hashtag" -> event.isTaggedHash(result.toString())
                else -> false
            }
        } else {
            return false
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyNewPrivateList(context: Context) {
        val channelId = "pokey_alerts_channel"
        val channelName = "Alerts"
        val importance = NotificationManager.IMPORTANCE_DEFAULT

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existingChannel = notificationManager.getNotificationChannel(channelId)
        if (existingChannel == null) {
            val channel = NotificationChannel(channelId, channelName, importance)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "REFRESH"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.new_mute_list_notification_title))
            .setContentText(context.getString(R.string.new_mute_list_notification_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(11111, builder.build())
    }
}
