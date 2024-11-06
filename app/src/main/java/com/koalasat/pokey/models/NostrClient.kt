package com.koalasat.pokey.models

import android.content.Context
import android.util.Log
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.RelayEntity
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.EVENT_FINDER_TYPES
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import java.time.Instant

object NostrClient {
    private var subscriptionNotificationId = "subscriptionNotificationId"
    private var subscriptionInboxId = "inboxRelays"
    private var subscriptionReadId = "readRelays"

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
        getInboxLists(context)
        subscribeToInbox(context)
    }

    fun checkRelaysHealth(context: Context) {
        if (RelayPool.getAll().isEmpty()) {
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

    fun deleteRelay(context: Context, url: String, kind: Int) {
        val db = AppDatabase.getDatabase(context, Pokey.getInstance().getHexKey())
        db.applicationDao().deleteRelayByUrl(url, kind)
        val relayStillExists = db.applicationDao().getReadRelays().any { it.url == url }
        val relay = RelayPool.getRelay(url)
        if (!relayStillExists && relay != null) {
            RelayPool.removeRelay(relay)
        }
    }

    fun addRelay(context: Context, url: String, kind: Int) {
        val db = AppDatabase.getDatabase(context, Pokey.getInstance().getHexKey())
        val existsRelay = db.applicationDao().existsRelay(url, kind)

        if (existsRelay < 1) {
            val entity = RelayEntity(id = 0, url, kind = kind, createdAt = 0, read = 1, write = 1)
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
                getInboxLists(context)
                subscribeToInbox(context)
            }
        }
    }

    private fun subscribeToInbox(context: Context) {
        val hexKey = Pokey.getInstance().getHexKey()
        if (hexKey.isEmpty()) return

        val db = AppDatabase.getDatabase(context, Pokey.getInstance().getHexKey())
        var latestNotification = db.applicationDao().getLatestNotification()
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
    }

    fun reconnectInbox(context: Context, kind: Int) {
        Log.d("Pokey", "reconnectInbox")
        val db = AppDatabase.getDatabase(context, Pokey.getInstance().getHexKey())
        db.applicationDao().deleteRelaysByKind(kind)
        Client.close(subscriptionInboxId)
        getInboxLists(context)
    }

    fun publishPrivateRelays(context: Context) {
        val kind = 10050

        val db = AppDatabase.getDatabase(context, Pokey.getInstance().getHexKey())
        val privateList = db.applicationDao().getRelaysByKind(kind)

        val pubKey = Pokey.getInstance().getHexKey()
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

    fun publishPublicRelays(context: Context) {
        val kind = 10002

        val db = AppDatabase.getDatabase(context, Pokey.getInstance().getHexKey())
        val publicList = db.applicationDao().getRelaysByKind(kind)

        val pubKey = Pokey.getInstance().getHexKey()
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

    private fun getInboxLists(context: Context) {
        val hexKey = Pokey.getInstance().getHexKey()
        if (hexKey.isEmpty()) return

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
            onResponse = { manageInboxRelays(context, it) },
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
            onResponse = { manageInboxRelays(context, it) },
        )
    }

    private fun connectRelays(context: Context) {
        val db = AppDatabase.getDatabase(context, Pokey.getInstance().getHexKey())
        var relays = db.applicationDao().getReadRelays()
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

    private fun manageInboxRelays(context: Context, event: Event) {
        val db = AppDatabase.getDatabase(context, Pokey.getInstance().getHexKey())
        val lastCreatedRelayAt = db.applicationDao().getLatestRelaysByKind(event.kind)

        if (lastCreatedRelayAt == null || lastCreatedRelayAt < event.createdAt) {
            db.applicationDao().getRelaysByKind(event.kind).forEach {
                deleteRelay(context, it.url, it.kind)
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
                    val entity = RelayEntity(id = 0, url = it[1], kind = event.kind, createdAt = event.createdAt, read = read, write = write)
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
}
