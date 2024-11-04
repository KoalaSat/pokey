package com.koalasat.pokey.ui.relays

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RelaysViewModel() : ViewModel() {
    private val regex = Regex("^(ws|wss)://[a-zA-Z0-9.-]+(:\\d+)?(/.*)?$")

    private val _newPublicRelay = MutableLiveData<String>()
    val newPublicRelay: LiveData<String> get() = _newPublicRelay
    private val _validationResultPublicRelay = MutableLiveData<Boolean>()
    val validationResultPublicRelay: LiveData<Boolean> get() = _validationResultPublicRelay

    private val _newPrivateRelay = MutableLiveData<String>()
    val newPrivateRelay: LiveData<String> get() = _newPrivateRelay
    private val _validationResultPrivateRelay = MutableLiveData<Boolean>()
    val validationResultPrivateRelay: LiveData<Boolean> get() = _validationResultPrivateRelay

    fun updateNewPublicRelay(text: String) {
        _newPublicRelay.value = text
        if (text.isNotEmpty()) validateNewPublicRelay()
    }

    private fun validateNewPublicRelay() {
        _validationResultPublicRelay.value = _newPublicRelay.value?.let { regex.matches(it) }
    }

    fun updateNewPrivateRelay(text: String) {
        _newPrivateRelay.value = text
        if (text.isNotEmpty()) validateNewPrivateRelay()
    }

    private fun validateNewPrivateRelay() {
        _validationResultPrivateRelay.value = _newPrivateRelay.value?.let { regex.matches(it) }
    }
}
