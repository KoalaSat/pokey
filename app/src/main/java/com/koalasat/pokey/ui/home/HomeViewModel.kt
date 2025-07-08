package com.koalasat.pokey.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.UserEntity
import com.koalasat.pokey.models.EncryptedStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>().applicationContext

    private val _accountList = MutableLiveData<List<UserEntity>>().apply { emptyList<UserEntity>() }
    val accountList: LiveData<List<UserEntity>> get() = _accountList

    private val _subscription = MutableLiveData<String>().apply { EncryptedStorage.inboxSubscription.value }
    val subscription: LiveData<String> get() = _subscription

    private val _serviceStart = MutableLiveData<Boolean>()
    val serviceStart: LiveData<Boolean> get() = _serviceStart

    init {
        _serviceStart.postValue(Pokey.isEnabled.value)

        EncryptedStorage.inboxSubscription.observeForever { value ->
            _subscription.postValue(value)
        }

        loadAccounts()
    }

    fun loadAccounts() {
        CoroutineScope(Dispatchers.IO).launch {
            _accountList.postValue(emptyList<UserEntity>())
            val dao = appContext?.let { AppDatabase.getDatabase(it, "common").applicationDao() }
            if (dao != null) {
                withContext(Dispatchers.IO) {
                    val users = dao.getUsers()
                    withContext(Dispatchers.Main) {
                        _accountList.postValue(users)
                        if (users.isNotEmpty()) {
                            if (EncryptedStorage.inboxPubKey.value?.isEmpty() == true) EncryptedStorage.updateInboxPubKey(users.first().toString())
                            if (EncryptedStorage.mutePubKey.value?.isEmpty() == true) EncryptedStorage.updateMutePubKey(users.first().toString())
                        }
                    }
                }
            }
        }
    }

    fun updateServiceStart(value: Boolean) {
        if (value) {
            Pokey.getInstance().startService()
        } else {
            Pokey.getInstance().stopService()
        }
        _serviceStart.postValue(value)
    }
}
