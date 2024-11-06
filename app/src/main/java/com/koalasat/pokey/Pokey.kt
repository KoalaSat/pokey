package com.koalasat.pokey

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.koalasat.pokey.models.EncryptedStorage
import com.koalasat.pokey.models.NostrClient
import com.koalasat.pokey.service.NotificationsService
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.Nip19Bech32.uriToRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class Pokey : Application() {
    private val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this

        updateIsEnabled(isForegroundServiceEnabled(this))

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
        saveForegroundServicePreference(this, true)
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
            val sharedPreferences: SharedPreferences = context.getSharedPreferences("PokeyPreferences", Context.MODE_PRIVATE)
            return sharedPreferences.getBoolean("foreground_service_enabled", false)
        }

        private fun saveForegroundServicePreference(context: Context, value: Boolean) {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences("PokeyPreferences", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putBoolean("foreground_service_enabled", value)
            editor.apply()
            updateIsEnabled(value)
        }
    }
}
