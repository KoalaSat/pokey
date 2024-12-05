package com.koalasat.pokey.ui.configuration

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.models.EncryptedStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConfigurationViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>().applicationContext

    private val _userHexPubKey = MutableLiveData<String>()
    val userHexPubKey: LiveData<String> = _userHexPubKey

    private val _broadcast = MutableLiveData<Boolean>().apply { value = EncryptedStorage.broadcast.value }
    val broadcast: LiveData<Boolean> = _broadcast

    private val _newReplies = MutableLiveData<Boolean>()
    val newReplies: LiveData<Boolean> = _newReplies

    private val _newZaps = MutableLiveData<Boolean>()
    val newZaps: LiveData<Boolean> = _newZaps

    private val _newQuotes = MutableLiveData<Boolean>()
    val newQuotes: LiveData<Boolean> = _newQuotes

    private val _newReactions = MutableLiveData<Boolean>()
    val newReactions: LiveData<Boolean> = _newReactions

    private val _newPrivate = MutableLiveData<Boolean>()
    val newPrivate: LiveData<Boolean> = _newPrivate

    private val _newMentions = MutableLiveData<Boolean>()
    val newMentions: LiveData<Boolean> = _newMentions

    private val _newReposts = MutableLiveData<Boolean>()
    val newReposts: LiveData<Boolean> = _newReposts

    init {
        EncryptedStorage.broadcast.observeForever { value ->
            _broadcast.postValue(value)
        }

        _userHexPubKey.observeForever {
            refreshData()
        }
    }

    fun updateBroadcast(value: Boolean) {
        _broadcast.postValue(value)
        EncryptedStorage.updateBroadcast(value)
    }

    fun updateNotifyReplies(value: Boolean) {
        _newReplies.postValue(value)
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(appContext, "common").applicationDao()
            val activeUser = dao.getUser(userHexPubKey.value.toString())
            if (activeUser != null) {
                activeUser.notifyReplies = if (value) 1 else 0
                dao.updateUser(activeUser)
            }
        }
    }

    fun updateNotifyReactions(value: Boolean) {
        _newReactions.postValue(value)
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(appContext, "common").applicationDao()
            val activeUser = dao.getUser(userHexPubKey.value.toString())
            if (activeUser != null) {
                activeUser.notifyReactions = if (value) 1 else 0
                dao.updateUser(activeUser)
            }
        }
    }

    fun updateNotifyPrivate(value: Boolean) {
        _newPrivate.postValue(value)
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(appContext, "common").applicationDao()
            val activeUser = dao.getUser(userHexPubKey.value.toString())
            if (activeUser != null) {
                activeUser.notifyPrivate = if (value) 1 else 0
                dao.updateUser(activeUser)
            }
        }
    }

    fun updateNotifyZaps(value: Boolean) {
        _newZaps.postValue(value)
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(appContext, "common").applicationDao()
            val activeUser = dao.getUser(userHexPubKey.value.toString())
            if (activeUser != null) {
                activeUser.notifyZaps = if (value) 1 else 0
                dao.updateUser(activeUser)
            }
        }
    }

    fun updateNotifyQuotes(value: Boolean) {
        _newQuotes.postValue(value)
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(appContext, "common").applicationDao()
            val activeUser = dao.getUser(userHexPubKey.value.toString())
            if (activeUser != null) {
                activeUser.notifyQuotes = if (value) 1 else 0
                dao.updateUser(activeUser)
            }
        }
    }

    fun updateNotifyMentions(value: Boolean) {
        _newMentions.postValue(value)
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(appContext, "common").applicationDao()
            val activeUser = dao.getUser(userHexPubKey.value.toString())
            if (activeUser != null) {
                activeUser.notifyMentions = if (value) 1 else 0
                dao.updateUser(activeUser)
            }
        }
    }

    fun updateNotifyReposts(value: Boolean) {
        _newReposts.postValue(value)
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(appContext, "common").applicationDao()
            val activeUser = dao.getUser(userHexPubKey.value.toString())
            if (activeUser != null) {
                activeUser.notifyReposts = if (value) 1 else 0
                dao.updateUser(activeUser)
            }
        }
    }

    fun updateUserHexPubKey(value: String) {
        _userHexPubKey.postValue(value)
    }

    fun refreshData() {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(appContext, "common").applicationDao()
            val activeUser = dao.getUser(userHexPubKey.value.toString())
            if (activeUser != null) {
                val mainHandler = Handler(Looper.getMainLooper())
                mainHandler.post {
                    _newReplies.postValue(activeUser.notifyReplies == 1)
                    _newZaps.postValue(activeUser.notifyZaps == 1)
                    _newQuotes.postValue(activeUser.notifyQuotes == 1)
                    _newReactions.postValue(activeUser.notifyReactions == 1)
                    _newPrivate.postValue(activeUser.notifyPrivate == 1)
                    _newMentions.postValue(activeUser.notifyMentions == 1)
                    _newReposts.postValue(activeUser.notifyReposts == 1)
                }
            }
        }
    }
}
