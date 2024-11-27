package com.koalasat.pokey.models

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PrefKeys {
    const val NOSTR_PUBKEY = "nostr_pubkey"
    const val NOSTR_AVATAR = "nostr_avatar"
    const val NOSTR_BROADCAST = "broadcast"
    const val EXTERNAL_SIGNER = "external_signer"
}
object DefaultKeys {
    const val BROADCAST = true
    const val EXTERNAL_SIGNER = "com.greenart7c3.nostrsigner"
}

object EncryptedStorage {
    private const val PREFERENCES_NAME = "secret_keeper"

    private lateinit var sharedPreferences: SharedPreferences

    private val _pubKey = MutableLiveData<String>()
    val pubKey: LiveData<String> get() = _pubKey

    private val _avatar = MutableLiveData<String>()
    val avatar: LiveData<String> get() = _avatar

    private val _broadcast = MutableLiveData<Boolean>().apply { DefaultKeys.BROADCAST }
    val broadcast: LiveData<Boolean> get() = _broadcast

    private val _externalSigner = MutableLiveData<String>().apply { DefaultKeys.EXTERNAL_SIGNER }
    val externalSigner: LiveData<String> get() = _externalSigner

    fun init(context: Context) {
        val masterKey: MasterKey =
            MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as EncryptedSharedPreferences

        _pubKey.value = sharedPreferences.getString(PrefKeys.NOSTR_PUBKEY, "")
        _avatar.value = sharedPreferences.getString(PrefKeys.NOSTR_AVATAR, "")
        _broadcast.value = sharedPreferences.getBoolean(PrefKeys.NOSTR_BROADCAST, DefaultKeys.BROADCAST)
        _externalSigner.value = sharedPreferences.getString(PrefKeys.EXTERNAL_SIGNER, DefaultKeys.EXTERNAL_SIGNER) ?: DefaultKeys.EXTERNAL_SIGNER
    }

    fun updateExternalSigner(newValue: String) {
        sharedPreferences.edit().putString(PrefKeys.EXTERNAL_SIGNER, newValue).apply()
        _externalSigner.value = newValue
    }

    fun updatePubKey(newValue: String) {
        sharedPreferences.edit().putString(PrefKeys.NOSTR_PUBKEY, newValue).apply()
        _pubKey.value = newValue
    }

    fun updateAvatar(newValue: String) {
        sharedPreferences.edit().putString(PrefKeys.NOSTR_AVATAR, newValue).apply()
        _avatar.value = newValue
    }

    fun updateBroadcast(newValue: Boolean) {
        sharedPreferences.edit().putBoolean(PrefKeys.NOSTR_BROADCAST, newValue).apply()
        _broadcast.value = newValue
    }
}
