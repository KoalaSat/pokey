package com.koalasat.pokey.models

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.R
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.signers.ExternalSignerLauncher
import com.vitorpamplona.quartz.signers.SignerType
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.get
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

object ExternalSigner {
    const val EXTERNAL_SIGNER = "com.greenart7c3.nostrsigner"
    private lateinit var nostrSignerLauncher: ActivityResultLauncher<Intent>
    private var externalSignerLaunchers = ConcurrentHashMap<String, ExternalSignerLauncher>()
    private var intents = ConcurrentHashMap<String, String>()

    fun init(activity: AppCompatActivity) {
        nostrSignerLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                Log.e("Pokey", "ExternalSigner result error: ${result.resultCode}")
                Toast.makeText(activity, activity.getString(R.string.amber_not_found), Toast.LENGTH_SHORT).show()
            } else {
                result.data?.let {
                    val id = it.getStringExtra("id")
                    val pubKey = intents.remove(id) ?: ""
                    var externalSignerLauncher = externalSignerLaunchers[pubKey]
                    externalSignerLauncher?.newResult(it)
                }
            }
        }
        startLauncher("")
    }

    fun savePubKey(onReady: (pubKey: String) -> Unit) {
        var externalSignerLauncher = externalSignerLaunchers[""]
        externalSignerLauncher?.openSignerApp(
            "",
            SignerType.GET_PUBLIC_KEY,
            "",
            UUID.randomUUID().toString(),
        ) { result ->
            val split = result.split("-")
            val pubkey = split.first().toString()
            if (pubkey.isNotEmpty()) {
                val hexPub = NostrClient.parseNpub(pubkey).toString()
                startLauncher(hexPub)
                onReady(hexPub)
            }
        }
    }

    fun auth(hexKey: String, relayUrl: String, challenge: String, onReady: (Event) -> Unit) {
        var externalSignerLauncher = externalSignerLaunchers[hexKey]

        val createdAt = TimeUtils.now()
        val kind = 22242
        val content = ""
        val tags =
            arrayOf(
                arrayOf("relay", relayUrl),
                arrayOf("challenge", challenge),
            )
        val id = Event.generateId(hexKey, createdAt, kind, tags, content).toHexKey()
        val event =
            Event(
                id = id,
                pubKey = hexKey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content,
                sig = "",
            )
        externalSignerLauncher?.openSigner(
            event,
        ) {
            onReady(
                Event(
                    id = id,
                    pubKey = hexKey,
                    createdAt = createdAt,
                    kind = kind,
                    tags = tags,
                    content = content,
                    sig = it,
                ),
            )
        }
    }

    fun sign(event: Event, onReady: (String) -> Unit) {
        var externalSignerLauncher = externalSignerLaunchers[event.pubKey]
        externalSignerLauncher?.openSigner(
            event,
            onReady,
        )
    }

    fun decrypt(event: Event, onReady: (String) -> Unit) {
        var externalSignerLauncher = externalSignerLaunchers[event.pubKey]
        val id = UUID.randomUUID().toString()
        intents.put(id, event.pubKey)
        externalSignerLauncher?.openSignerApp(
            event.content,
            SignerType.NIP04_DECRYPT,
            event.pubKey,
            id,
            onReady,
        )
    }

    fun encrypt(content: String, pubKey: String, onReady: (String) -> Unit) {
        var externalSignerLauncher = externalSignerLaunchers[pubKey]
        val id = UUID.randomUUID().toString()
        intents.put(id, pubKey)
        externalSignerLauncher?.openSignerApp(
            content,
            SignerType.NIP04_ENCRYPT,
            pubKey,
            id,
            onReady,
        )
    }

    fun startLauncher(pubKey: String) {
        val externalSignerLauncher = ExternalSignerLauncher(pubKey, signerPackageName = EXTERNAL_SIGNER)
        externalSignerLauncher.registerLauncher(
            launcher = {
                try {
                    nostrSignerLauncher.launch(it)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("Pokey", "Error opening Signer app", e)
                }
            },
            contentResolver = { Pokey.getInstance().contentResolverFn() },
        )
        externalSignerLaunchers.put(pubKey, externalSignerLauncher)
    }
}
