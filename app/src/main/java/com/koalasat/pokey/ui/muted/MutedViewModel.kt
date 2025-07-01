package com.koalasat.pokey.ui.muted

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MutedViewModel() : ViewModel() {
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
        _newPublicRelay.postValue(text)
        if (text.isNotEmpty()) validateNewPublicRelay()
    }

    private fun validateNewPublicRelay() {
        _validationResultPublicRelay.postValue(_newPublicRelay.value?.let { regex.matches(it) })
    }

    fun updateNewPrivateRelay(text: String) {
        _newPrivateRelay.postValue(text)
        if (text.isNotEmpty()) validateNewPrivateRelay()
    }

    private fun validateNewPrivateRelay() {
        _validationResultPrivateRelay.postValue(_newPrivateRelay.value?.let { regex.matches(it) })
    }
}
