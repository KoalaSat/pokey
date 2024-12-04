package com.koalasat.pokey.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.database.AppDatabase
import com.koalasat.pokey.database.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>().applicationContext

    private val _accountList = MutableLiveData<List<UserEntity>>().apply { emptyList<UserEntity>() }
    val accountList: LiveData<List<UserEntity>> get() = _accountList

    private val _serviceStart = MutableLiveData<Boolean>()
    val serviceStart: LiveData<Boolean> get() = _serviceStart

    init {
        _serviceStart.postValue(Pokey.isEnabled.value)

        loadAccouts()
    }

    fun loadAccouts() {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = appContext?.let { AppDatabase.getDatabase(it, "common").applicationDao() }
            if (dao != null) {
                withContext(Dispatchers.IO) {
                    val accounst = dao.getUsers()
                    withContext(Dispatchers.Main) {
                        _accountList.postValue(accounst)
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
