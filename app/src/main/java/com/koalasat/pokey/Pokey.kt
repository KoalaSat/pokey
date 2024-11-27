package com.koalasat.pokey

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.UserEntity
import com.koalasat.pokey.models.EncryptedStorage
import com.koalasat.pokey.models.NostrClient
import com.koalasat.pokey.models.NostrClient.getNip05Content
import com.koalasat.pokey.service.NotificationsService
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.Nip19Bech32.uriToRoute
import kotlin.String
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONException

class Pokey : Application() {
    private val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this

        updateIsEnabled(isForegroundServiceEnabled(this))

        migrateUserToDB()

        NostrClient.init()
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationIOScope.cancel()
    }

    fun startService() {
        this.startForegroundService(
            Intent(
                this,
                NotificationsService::class.java,
            ),
        )

        saveForegroundServicePreference(this@Pokey, true)

        CoroutineScope(Dispatchers.IO).launch {
            val hexKey = getHexKey()
            val dao = AppDatabase.getDatabase(this@Pokey, hexKey).applicationDao()
            var newUser = UserEntity(
                id = 0,
                hexPub = hexKey,
                name = null,
                avatar = null,
                createdAt = null,
            )
            dao.insertUser(newUser)
            Log.e("Pokey", newUser.toString())
            getNip05Content(
                hexKey,
                onResponse = {
                    try {
                        CoroutineScope(Dispatchers.IO).launch {
                            newUser.name = it?.getString("name")
                            newUser.avatar = it?.getString("picture")
                            newUser.createdAt = it?.getLong("created_at")
                            if (dao.updateUser(newUser) == 1 && newUser.avatar?.isNotEmpty() == true) {
                                EncryptedStorage.updateAvatar(newUser.avatar.toString())
                            }
                        }
                    } catch (e: JSONException) {
                    }
                },
            )
        }
    }

    fun stopService() {
        val intent = Intent(applicationContext, NotificationsService::class.java)
        applicationContext.stopService(intent)
        saveForegroundServicePreference(this, false)
    }

    fun contentResolverFn(): ContentResolver = contentResolver

    fun getHexKey(): String {
        val pubKey = EncryptedStorage.pubKey.value
        var hexKey = ""
        val parseReturn = uriToRoute(pubKey)
        when (val parsed = parseReturn?.entity) {
            is Nip19Bech32.NPub -> {
                hexKey = parsed.hex
            }
        }
        return hexKey
    }

    companion object {
        private val _isEnabled = MutableLiveData(false)
        val isEnabled: LiveData<Boolean> get() = _isEnabled
        private val _loadingPublicRelays = MutableLiveData(false)
        val loadingPublicRelays: LiveData<Boolean> get() = _loadingPublicRelays
        private val _loadingPrivateRelays = MutableLiveData(false)
        val loadingPrivateRelays: LiveData<Boolean> get() = _loadingPrivateRelays

        @Volatile
        private var instance: Pokey? = null

        fun getInstance(): Pokey =
            instance ?: synchronized(this) {
                instance ?: Pokey().also { instance = it }
            }

        fun updateIsEnabled(value: Boolean) {
            _isEnabled.value = value
        }

        fun updateLoadingPublicRelays(value: Boolean) {
            _loadingPublicRelays.postValue(value)
        }

        fun updateLoadingPrivateRelays(value: Boolean) {
            _loadingPrivateRelays.postValue(value)
        }

        fun isForegroundServiceEnabled(context: Context): Boolean {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences("PokeyPreferences", MODE_PRIVATE)
            return sharedPreferences.getBoolean("foreground_service_enabled", false)
        }

        private fun saveForegroundServicePreference(context: Context, value: Boolean) {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences("PokeyPreferences", MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putBoolean("foreground_service_enabled", value)
            editor.apply()
            updateIsEnabled(value)
        }
    }

    private fun migrateUserToDB() {
        Log.e("Pokey", "migrateUserToDB")
        CoroutineScope(Dispatchers.IO).launch {
            if (EncryptedStorage.pubKey.value?.isNotEmpty() == true) {
                Log.e("Pokey", "EncryptedStorage.pubKey.value")
                val hexKey = getHexKey()
                val dao = AppDatabase.getDatabase(this@Pokey, hexKey).applicationDao()
                val existUser = dao.getUser(hexKey)
                Log.e("Pokey", existUser.toString())
                if (existUser == null) {
                    var newUser = UserEntity(
                        id = 0,
                        hexPub = hexKey,
                        name = null,
                        avatar = null,
                        createdAt = null,
                        notifyReplies = if (EncryptedStorage.notifyReplies.value == true) 1 else 0,
                        notifyPrivate = if (EncryptedStorage.notifyPrivate.value == true) 1 else 0,
                        notifyZaps = if (EncryptedStorage.notifyZaps.value == true) 1 else 0,
                        notifyQuotes = if (EncryptedStorage.notifyQuotes.value == true) 1 else 0,
                        notifyReactions = if (EncryptedStorage.notifyReactions.value == true) 1 else 0,
                        notifyMentions = if (EncryptedStorage.notifyMentions.value == true) 1 else 0,
                        notifyReposts = if (EncryptedStorage.notifyResposts.value == true) 1 else 0,
                    )
                    dao.insertUser(newUser)
                    Log.e("Pokey", newUser.toString())
                    getNip05Content(
                        hexKey,
                        onResponse = {
                            try {
                                CoroutineScope(Dispatchers.IO).launch {
                                    newUser.name = it?.getString("name")
                                    newUser.avatar = it?.getString("picture")
                                    newUser.createdAt = it?.getLong("created_at")
                                    if (dao.updateUser(newUser) == 1 && newUser.avatar?.isNotEmpty() == true) {
                                        EncryptedStorage.updateAvatar(newUser.avatar.toString())
                                    }
                                }
                            } catch (e: JSONException) {
                            }
                        },
                    )
                }
            }
        }
    }
}
