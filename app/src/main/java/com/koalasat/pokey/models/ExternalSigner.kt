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
import com.vitorpamplona.quartz.events.RelayAuthEvent
import com.vitorpamplona.quartz.signers.ExternalSignerLauncher
import com.vitorpamplona.quartz.signers.NostrSignerExternal
import com.vitorpamplona.quartz.signers.SignerType
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

object ExternalSigner {
    private lateinit var nostrSignerLauncher: ActivityResultLauncher<Intent>
    private lateinit var externalSignerLauncher: NostrSignerExternal

    fun init(activity: AppCompatActivity) {
        nostrSignerLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                Log.e("Pokey", "ExternalSigner result error: ${result.resultCode}")
                Toast.makeText(activity, activity.getString(R.string.amber_not_found), Toast.LENGTH_SHORT).show()
            } else {
                result.data?.let { externalSignerLauncher.launcher.newResult(it) }
            }
        }

        startLauncher()
    }

    fun savePubKey() {
        externalSignerLauncher.launcher.openSignerApp(
            "",
            SignerType.GET_PUBLIC_KEY,
            "",
            UUID.randomUUID().toString(),
        ) { result ->
            val split = result.split("-")
            val pubkey = split.first()
            if (split.first().isNotEmpty()) {
                EncryptedStorage.updatePubKey(pubkey)
                if (split.size > 1) {
                    EncryptedStorage.updateExternalSigner(split[1])
                }
                startLauncher()
            }
        }
    }

    fun auth(relayUrl: String, challenge: String, onReady: (RelayAuthEvent) -> Unit) {
        val createdAt = TimeUtils.now()
        val kind = 22242
        val content = ""
        val tags =
            arrayOf(
                arrayOf("relay", relayUrl),
                arrayOf("challenge", challenge),
            )

        externalSignerLauncher.sign(
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
            onReady = onReady,
        )
    }

    private fun startLauncher() {
        val pubKey = Pokey.getInstance().getHexKey()
        var externalSignerPackage = EncryptedStorage.externalSigner.value
        if (externalSignerPackage == null) externalSignerPackage = ""
        if (pubKey.isEmpty()) externalSignerPackage = ""
        externalSignerLauncher = NostrSignerExternal(pubKey, ExternalSignerLauncher(pubKey, signerPackageName = externalSignerPackage))
        externalSignerLauncher.launcher.registerLauncher(
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
    }
}
