package com.qeetgroup.qeetid.system

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Codes on the clipboard: marked sensitive (Android 13 and later keep them out of the clipboard preview and keyboard
 * suggestions) and cleared [CLEAR_AFTER_MILLIS] after copying, when the clipboard still holds the code — or when the
 * app can no longer read the clipboard to tell (Android lets only the app in front read it).
 */
class CodeClipboard(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var clearing: Job? = null

    /** Copies [code] (as an account's code from a menu, not the code's own tap) and schedules the clear. */
    fun copy(
        code: String,
        label: String,
    ) {
        val clip = ClipData.newPlainText(label, code)
        clip.description.extras =
            PersistableBundle().apply {
                putBoolean(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ClipDescription.EXTRA_IS_SENSITIVE else SENSITIVE, true)
            }
        manager()?.setPrimaryClip(clip)
        clearLater(code)
    }

    /** Schedules the clear of [code], copied by Qeetrix's code (QeetrixOTPCode copies on tap itself). */
    fun clearLater(code: String) {
        clearing?.cancel()
        clearing =
            scope.launch {
                delay(CLEAR_AFTER_MILLIS)
                val manager = manager() ?: return@launch
                val current =
                    try {
                        manager.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.text
                            ?.toString()
                    } catch (_: SecurityException) {
                        null
                    }
                if (current == null || current == code) manager.clearPrimaryClip()
            }
    }

    private fun manager(): ClipboardManager? = context.getSystemService(ClipboardManager::class.java)

    companion object {
        const val CLEAR_AFTER_MILLIS = 30_000L

        /** ClipDescription.EXTRA_IS_SENSITIVE's key, which some keyboards and OEM previews read below Android 13. */
        private const val SENSITIVE = "android.content.extra.IS_SENSITIVE"
    }
}
