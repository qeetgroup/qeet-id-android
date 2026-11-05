package com.qeetgroup.qeetid.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qeetgroup.qeetid.AppContainer
import com.qeetgroup.qeetid.approvals.LinkSummary
import com.qeetgroup.qeetid.data.api.UnlinkResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** How unlinking went. */
enum class UnlinkOutcome {
    Unlinked,

    /** The server had already removed the phone; it is cleared here too. */
    AlreadyRemoved,

    /** Not unlinked: the phone is still linked and can still approve. */
    FailedNetwork,
    FailedRefused,
}

/**
 * Settings (Screens 22 and 23): the link, unlinking, app lock and hidden codes. Unlinking removes the phone on the
 * server with its own credential first, and only then forgets the link here; if the server cannot be reached the
 * phone stays linked, and says so.
 */
class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val link: StateFlow<LinkSummary?> = container.links.summary
    val appLock: StateFlow<Boolean> = container.preferences.appLock
    val hideCodes: StateFlow<Boolean> = container.preferences.hideCodes
    private val _unlinking = MutableStateFlow(false)
    val unlinking: StateFlow<Boolean> = _unlinking.asStateFlow()
    private val outcomes = Channel<UnlinkOutcome>(Channel.BUFFERED)
    val unlinkOutcomes: Flow<UnlinkOutcome> = outcomes.receiveAsFlow()

    init {
        viewModelScope.launch { container.links.get() }
    }

    fun setHideCodes(value: Boolean) = container.preferences.setHideCodes(value)

    /** After the person was verified (turning the lock on or off asks first). */
    fun setAppLock(value: Boolean) {
        container.preferences.setAppLock(value)
        if (!value) container.lock.release()
    }

    fun unlink() {
        if (_unlinking.value) return
        _unlinking.value = true
        viewModelScope.launch {
            val link = container.links.get()
            val outcome =
                if (link == null) {
                    UnlinkOutcome.AlreadyRemoved
                } else {
                    when (container.api.unlink(link.deviceId, link.userId, link.deviceToken)) {
                        UnlinkResult.Removed -> UnlinkOutcome.Unlinked
                        UnlinkResult.AlreadyRemoved -> UnlinkOutcome.AlreadyRemoved
                        UnlinkResult.Network -> UnlinkOutcome.FailedNetwork
                        is UnlinkResult.Refused -> UnlinkOutcome.FailedRefused
                    }
                }
            if (outcome == UnlinkOutcome.Unlinked || outcome == UnlinkOutcome.AlreadyRemoved) {
                container.links.clear()
                container.pushTokens.stop()
            }
            _unlinking.value = false
            outcomes.send(outcome)
        }
    }
}
