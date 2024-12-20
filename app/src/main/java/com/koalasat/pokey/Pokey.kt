package com.koalasat.pokey

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.koalasat.pokey.models.NostrClient
import com.koalasat.pokey.service.NotificationsService
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

        saveForegroundServicePreference(this@Pokey, true)
    }

    fun stopService() {
        val intent = Intent(applicationContext, NotificationsService::class.java)
        applicationContext.stopService(intent)
        saveForegroundServicePreference(this, false)
    }

    fun contentResolverFn(): ContentResolver = contentResolver

    companion object {
        private val _isEnabled = MutableLiveData(false)
        val isEnabled: LiveData<Boolean> get() = _isEnabled
        private val _loadingPublicRelays = MutableLiveData(false)
        val loadingPublicRelays: LiveData<Boolean> get() = _loadingPublicRelays
        private val _loadingPrivateRelays = MutableLiveData(false)
        val loadingPrivateRelays: LiveData<Boolean> get() = _loadingPrivateRelays
        private val _lastNotificationTime = MutableLiveData<Long>()
        val lastNotificationTime: LiveData<Long> get() = _lastNotificationTime

        @Volatile
        private var instance: Pokey? = null

        fun getInstance(): Pokey =
            instance ?: synchronized(this) {
                instance ?: Pokey().also { instance = it }
            }

        fun updateIsEnabled(value: Boolean) {
            _isEnabled.postValue(value)
        }

        fun updateLoadingPublicRelays(value: Boolean) {
            _loadingPublicRelays.postValue(value)
        }

        fun updateLoadingPrivateRelays(value: Boolean) {
            _loadingPrivateRelays.postValue(value)
        }

        fun updateNewPrivateRelay(time: Long) {
            _lastNotificationTime.postValue(time)
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
}
