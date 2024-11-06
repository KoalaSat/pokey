package com.koalasat.pokey.utils

import android.content.Context
import android.content.res.Configuration

fun isDarkThemeEnabled(context: Context): Boolean {
    return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
