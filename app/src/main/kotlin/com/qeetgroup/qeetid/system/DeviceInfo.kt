package com.qeetgroup.qeetid.system

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.qeetgroup.qeetid.R

object DeviceInfo {
    /** The name the person gave the phone (Settings › About phone), or its model: prefilled when linking. */
    fun name(context: Context): String =
        Settings.Global
            .getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: Build.MODEL.orEmpty().ifBlank { context.getString(R.string.device_name_fallback) }
}
