package com.koalasat.pokey.models

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object PrefKeys {
    const val NOSTR_PUBKEY = "nostr_pubkey"
    const val NOTIFY_REPLIES = "notify_replies"
    const val NOTIFY_PRIVATE = "notify_private"
    const val NOTIFY_ZAPS = "notify_zaps"
    const val NOTIFY_QUOTES = "notify_quotes"
    const val NOTIFY_REACTIONS = "notify_reactions"
    const val NOTIFY_MENTIONS = "notify_mentions"
    const val NOTIFY_REPOSTS = "notify_reposts"
    const val NOTIFY_FOLLOWS = "notify_follows"
}
object DefaultKeys {
    const val NOTIFY_REPLIES = true
    const val NOTIFY_REACTIONS = true
    const val NOTIFY_PRIVATE = true
    const val NOTIFY_ZAPS = true
    const val NOTIFY_QUOTES = true
    const val NOTIFY_MENTIONS = true
    const val NOTIFY_REPOSTS = true
    const val NOTIFY_FOLLOWS = true
}

object EncryptedStorage {
    private const val PREFERENCES_NAME = "secret_keeper"

    private lateinit var sharedPreferences: SharedPreferences

    private val _pubKey = MutableLiveData<String>()
    val pubKey: LiveData<String> get() = _pubKey

    private val _notifyReplies = MutableLiveData<Boolean>().apply { DefaultKeys.NOTIFY_REPLIES }
    val notifyReplies: LiveData<Boolean> get() = _notifyReplies
    private val _notifyReactions = MutableLiveData<Boolean>().apply { DefaultKeys.NOTIFY_REACTIONS }
    val notifyReactions: LiveData<Boolean> get() = _notifyReactions
    private val _notifyPrivate = MutableLiveData<Boolean>().apply { DefaultKeys.NOTIFY_PRIVATE }
    val notifyPrivate: LiveData<Boolean> get() = _notifyPrivate
    private val _notifyZaps = MutableLiveData<Boolean>().apply { DefaultKeys.NOTIFY_ZAPS }
    val notifyZaps: LiveData<Boolean> get() = _notifyZaps
    private val _notifyQuotes = MutableLiveData<Boolean>().apply { DefaultKeys.NOTIFY_QUOTES }
    val notifyQuotes: LiveData<Boolean> get() = _notifyQuotes
    private val _notifyMentions = MutableLiveData<Boolean>().apply { DefaultKeys.NOTIFY_MENTIONS }
    val notifyMentions: LiveData<Boolean> get() = _notifyMentions
    private val _notifyResposts = MutableLiveData<Boolean>().apply { DefaultKeys.NOTIFY_REPOSTS }
    val notifyResposts: LiveData<Boolean> get() = _notifyResposts
    private val _notifyFollows = MutableLiveData<Boolean>().apply { DefaultKeys.NOTIFY_FOLLOWS }
    val notifyFollows: LiveData<Boolean> get() = _notifyFollows

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
        _notifyReplies.value = sharedPreferences.getBoolean(PrefKeys.NOTIFY_REPLIES, DefaultKeys.NOTIFY_REPLIES)
        _notifyReactions.value = sharedPreferences.getBoolean(PrefKeys.NOTIFY_REACTIONS, DefaultKeys.NOTIFY_REACTIONS)
        _notifyPrivate.value = sharedPreferences.getBoolean(PrefKeys.NOTIFY_PRIVATE, DefaultKeys.NOTIFY_PRIVATE)
        _notifyZaps.value = sharedPreferences.getBoolean(PrefKeys.NOTIFY_ZAPS, DefaultKeys.NOTIFY_ZAPS)
        _notifyQuotes.value = sharedPreferences.getBoolean(PrefKeys.NOTIFY_QUOTES, DefaultKeys.NOTIFY_QUOTES)
        _notifyMentions.value = sharedPreferences.getBoolean(PrefKeys.NOTIFY_MENTIONS, DefaultKeys.NOTIFY_MENTIONS)
        _notifyResposts.value = sharedPreferences.getBoolean(PrefKeys.NOTIFY_REPOSTS, DefaultKeys.NOTIFY_REPOSTS)
        _notifyFollows.value = sharedPreferences.getBoolean(PrefKeys.NOTIFY_FOLLOWS, DefaultKeys.NOTIFY_FOLLOWS)
    }

    fun updatePubKey(newValue: String) {
        sharedPreferences.edit().putString(PrefKeys.NOSTR_PUBKEY, newValue).apply()
        _pubKey.value = newValue
    }

    fun updateNotifyReplies(newValue: Boolean) {
        sharedPreferences.edit().putBoolean(PrefKeys.NOTIFY_REPLIES, newValue).apply()
        _notifyReplies.value = newValue
    }

    fun updateNotifyReactions(newValue: Boolean) {
        sharedPreferences.edit().putBoolean(PrefKeys.NOTIFY_REACTIONS, newValue).apply()
        _notifyReactions.value = newValue
    }

    fun updateNotifyPrivate(newValue: Boolean) {
        sharedPreferences.edit().putBoolean(PrefKeys.NOTIFY_PRIVATE, newValue).apply()
        _notifyPrivate.value = newValue
    }

    fun updateNotifyZaps(newValue: Boolean) {
        sharedPreferences.edit().putBoolean(PrefKeys.NOTIFY_ZAPS, newValue).apply()
        _notifyZaps.value = newValue
    }

    fun updateNotifyQuotes(newValue: Boolean) {
        sharedPreferences.edit().putBoolean(PrefKeys.NOTIFY_QUOTES, newValue).apply()
        _notifyQuotes.value = newValue
    }

    fun updateNotifyMentions(newValue: Boolean) {
        sharedPreferences.edit().putBoolean(PrefKeys.NOTIFY_MENTIONS, newValue).apply()
        _notifyMentions.value = newValue
    }

    fun updateNotifyReposts(newValue: Boolean) {
        sharedPreferences.edit().putBoolean(PrefKeys.NOTIFY_REPOSTS, newValue).apply()
        _notifyResposts.value = newValue
    }

    fun updateNotifyFollows(newValue: Boolean) {
        sharedPreferences.edit().putBoolean(PrefKeys.NOTIFY_FOLLOWS, newValue).apply()
        _notifyResposts.value = newValue
    }
}
