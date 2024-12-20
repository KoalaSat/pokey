package com.koalasat.pokey.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>().applicationContext
}
