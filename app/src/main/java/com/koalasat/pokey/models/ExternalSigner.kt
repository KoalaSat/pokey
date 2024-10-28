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
import kotlin.coroutines.cancellation.CancellationException

object ExternalSigner {
    private lateinit var nostrSignerLauncher: ActivityResultLauncher<Intent>
    private var externalSignerLauncher: ExternalSignerLauncher = ExternalSignerLauncher("", signerPackageName = "")

    fun init(activity: AppCompatActivity) {
        nostrSignerLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                Log.e("Pokey", "ExternalSigner result error: ${result.resultCode}")
                Toast.makeText(activity, activity.getString(R.string.amber_not_found), Toast.LENGTH_SHORT).show()
            } else {
                result.data?.let { externalSignerLauncher.newResult(it) }
            }
        }
        externalSignerLauncher.registerLauncher(
            launcher = {
                try {
                    nostrSignerLauncher.launch(it) // This can remain if you still need to launch it
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("Pokey", "Error opening Signer app", e)
                }
            },
            contentResolver = { Pokey.getInstance().contentResolverFn() },
        )
    }

    fun savePubKey() {
        externalSignerLauncher.openSignerApp(
            "",
            SignerType.GET_PUBLIC_KEY,
            "",
            UUID.randomUUID().toString(),
        ) { result ->
            val split = result.split("-")
            val pubkey = split.first()
            if (split.first().isNotEmpty()) EncryptedStorage.updatePubKey(pubkey)
        }
    }

    fun auth(relayUrl: String, challenge: String, onReady: (String) -> Unit) {
        val pubKey = Pokey.getInstance().getHexKey()
        val createdAt = TimeUtils.now()
        val kind = 22242
        val content = ""
        val tags =
            arrayOf(
                arrayOf("relay", relayUrl),
                arrayOf("challenge", challenge),
            )
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
        externalSignerLauncher.openSigner(
            event,
            onReady,
        )
    }
}
