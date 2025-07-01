package com.koalasat.pokey.models

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PrefKeys {
    const val NOSTR_BROADCAST = "broadcast"
    const val NOSTR_MAX_PUBKEYS = "maxPubKeys"
    const val INBOX_PUBKEY = "inbox_pubkey"
    const val MUTE_PUBKEY = "mute_pubkey"
    const val INBOX_SUBSCRIPTION = "inbox_subscription"
}

object DefaultKeys {
    const val BROADCAST = true
    const val MAX_PUBKEYS = 10
}

object EncryptedStorage {
    private const val PREFERENCES_NAME = "secret_keeper"

    private lateinit var sharedPreferences: SharedPreferences

    private val _inboxSubscription = MutableLiveData<String>()
    val inboxSubscription: LiveData<String> get() = _inboxSubscription

    private val _inboxPubKey = MutableLiveData<String>()
    val inboxPubKey: LiveData<String> get() = _inboxPubKey

    private val _mutePubKey = MutableLiveData<String>()
    val mutePubKey: LiveData<String> get() = _mutePubKey

    private val _broadcast = MutableLiveData<Boolean>().apply { DefaultKeys.BROADCAST }
    val broadcast: LiveData<Boolean> get() = _broadcast

    private val _maxPubKeys = MutableLiveData<Int>().apply { DefaultKeys.MAX_PUBKEYS }
    val maxPubKeys: LiveData<Int> get() = _maxPubKeys

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

        _broadcast.postValue(sharedPreferences.getBoolean(PrefKeys.NOSTR_BROADCAST, DefaultKeys.BROADCAST))
        _maxPubKeys.postValue(sharedPreferences.getInt(PrefKeys.NOSTR_MAX_PUBKEYS, DefaultKeys.MAX_PUBKEYS))
        _inboxPubKey.postValue(sharedPreferences.getString(PrefKeys.INBOX_PUBKEY, ""))
        _mutePubKey.postValue(sharedPreferences.getString(PrefKeys.MUTE_PUBKEY, ""))
        _inboxSubscription.postValue(sharedPreferences.getString(PrefKeys.INBOX_SUBSCRIPTION, ""))
    }

    fun updateBroadcast(newValue: Boolean) {
        sharedPreferences.edit().putBoolean(PrefKeys.NOSTR_BROADCAST, newValue).apply()
        _broadcast.postValue(newValue)
    }

    fun updateMaxPubKeys(newValue: Int) {
        sharedPreferences.edit().putInt(PrefKeys.NOSTR_MAX_PUBKEYS, newValue).apply()
        _maxPubKeys.postValue(newValue)
    }

    fun updateInboxPubKey(pubKey: String) {
        sharedPreferences.edit().putString(PrefKeys.INBOX_PUBKEY, pubKey).apply()
        _inboxPubKey.postValue(pubKey)
    }

    fun updateMutePubKey(pubKey: String) {
        sharedPreferences.edit().putString(PrefKeys.MUTE_PUBKEY, pubKey).apply()
        _mutePubKey.postValue(pubKey)
    }

    fun updateInboxSubscription(value: String) {
        sharedPreferences.edit().putString(PrefKeys.INBOX_SUBSCRIPTION, value).apply()
        _inboxSubscription.postValue(value)
    }
}
