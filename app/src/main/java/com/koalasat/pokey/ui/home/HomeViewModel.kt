package com.koalasat.pokey.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.koalasat.pokey.Pokey
import com.koalasat.pokey.models.EncryptedStorage
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.Nip19Bech32.uriToRoute

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>().applicationContext

    private val _avatar = MutableLiveData<String>()
    val avatar: LiveData<String> get() = _avatar

    private val _npubInput = MutableLiveData<String>()
    val npubInput: LiveData<String> get() = _npubInput

    private val _serviceStart = MutableLiveData<Boolean>()
    val serviceStart: LiveData<Boolean> get() = _serviceStart

    private val _validationResult = MutableLiveData<Boolean>()
    val validationResult: LiveData<Boolean> get() = _validationResult

    init {
        _npubInput.value = EncryptedStorage.pubKey.value
        _serviceStart.value = Pokey.isEnabled.value
        EncryptedStorage.pubKey.observeForever { text ->
            _npubInput.value = text
        }
        EncryptedStorage.avatar.observeForever { text ->
            _avatar.value = text
        }
    }

    fun updateServiceStart(value: Boolean) {
        if (value) {
            Pokey.getInstance().startService()
        } else {
            Pokey.getInstance().stopService()
        }
        _serviceStart.value = value
    }

    fun updateNpubInput(text: String) {
        _npubInput.value = text
        validateNpubInput()
        if (_validationResult.value == true) {
            EncryptedStorage.updatePubKey(text)
        }
    }

    private fun validateNpubInput() {
        val parseReturn = uriToRoute(_npubInput.value)

        when (parseReturn?.entity) {
            is Nip19Bech32.NPub -> {
                _validationResult.value = true
                return
            }
        }

        _validationResult.value = false
    }
}
