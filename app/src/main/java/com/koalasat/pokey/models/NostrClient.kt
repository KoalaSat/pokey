package com.koalasat.pokey.models

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.MuteEntity
import com.koalasat.pokey.database.RelayEntity
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
import com.vitorpamplona.quartz.utils.TimeUtils
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

object NostrClient {
    private var subscriptionNotificationId = "pokeyNotificationId"
    private var subscriptionPrivateMessagId = "pokeyPrivateMessage"
    private var subscriptionLists = "pokeyLists"
    private var subscriptionMetaId = "pokeyMeta"

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

    fun publishPublicMute(hexPubKey: String, context: Context) {
        val kind = 10000

        val db = AppDatabase.getDatabase(context, "common")
        val publicMuteList = db.applicationDao().getMuteList(kind, hexPubKey)

        val pubKey = hexPubKey
        val createdAt = TimeUtils.now()
        val content = getPrivateMuteList(context)
        val tags = publicMuteList.map {
            arrayOf(it.tagType, it.entityId)
        }.toTypedArray()
        tags.plus(arrayOf("alt", "Mute List"))
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
            Log.d("Pokey", "Mute public list : ${signeEvent.toJson()}")
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

    private fun getLists(context: Context) {
        val db = AppDatabase.getDatabase(context, "common")
        val users = db.applicationDao().getUsers()
        val authors = users.map { it.hexPub }

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

    fun manageMuteList(context: Context, event: Event) {
        val db = AppDatabase.getDatabase(context, "common")
        savePrivateMuteList(context, event.content)
        db.applicationDao().deleteMuteList(event.kind, event.pubKey)
        event.tags.forEach {
            if (it.size > 1 && (it[0] == "p" || it[0] == "e")) {
                val muteEntity = MuteEntity(id = 0, kind = event.kind, tagType = it[0], entityId = it[1], private = 0, hexPub = event.pubKey)
                db.applicationDao().insertMute(muteEntity)
            }
        }
    }

    private fun getPrivateMuteList(context: Context): String {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("PokeyPreferences", Context.MODE_PRIVATE)
        return sharedPreferences.getString("private_mute_list", "").toString()
    }

    private fun savePrivateMuteList(context: Context, value: String) {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("PokeyPreferences", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("private_mute_list", value)
        editor.apply()
    }
}
