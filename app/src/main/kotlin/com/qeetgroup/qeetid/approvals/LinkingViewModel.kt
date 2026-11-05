package com.qeetgroup.qeetid.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qeetgroup.qeetid.AppContainer
import com.qeetgroup.qeetid.approvals.push.PushAvailability
import com.qeetgroup.qeetid.approvals.push.PushTokenSync
import com.qeetgroup.qeetid.data.api.ClaimResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Why linking did not happen. */
sealed interface LinkProblem {
    data object InvalidCode : LinkProblem

    data class Throttled(
        val seconds: Int,
    ) : LinkProblem

    data object Network : LinkProblem

    data object Refused : LinkProblem

    /** No push token: Google Play services missing, offline, or Firebase refused. */
    data object NoPushToken : LinkProblem

    /** This build has no push service at all. */
    data object Unavailable : LinkProblem
}

data class LinkingState(
    val linking: Boolean = false,
    val codeError: Boolean = false,
    val nameError: Boolean = false,
    val problem: LinkProblem? = null,
    val linked: Boolean = false,
)

/**
 * Links this phone with a pairing code (Screens 13 and 14). The code is the only credential: no access token, no
 * bearer. The person always confirms first — a scanned or opened link only fills the code in.
 */
class LinkingViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(LinkingState())
    val state: StateFlow<LinkingState> = _state.asStateFlow()

    val availability: PushAvailability get() = container.pushTokens.availability

    fun link(
        codeText: String,
        name: String,
    ) {
        if (_state.value.linking) return
        val code = PairingCode.from(codeText)
        val deviceName = name.trim().take(MAX_NAME)
        if (code == null || deviceName.isEmpty()) {
            _state.value = LinkingState(codeError = code == null, nameError = deviceName.isEmpty())
            return
        }
        if (availability == PushAvailability.Unavailable) {
            _state.value = LinkingState(problem = LinkProblem.Unavailable)
            return
        }
        _state.value = LinkingState(linking = true)
        viewModelScope.launch {
            val token = container.pushTokens.token()
            if (token == null) {
                _state.value = LinkingState(problem = LinkProblem.NoPushToken)
                return@launch
            }
            _state.value =
                when (val result = container.api.claimPairing(code, deviceName, token)) {
                    is ClaimResult.Linked -> {
                        container.links.save(
                            PhoneLink(
                                userId = result.userId,
                                deviceId = result.deviceId,
                                deviceToken = result.deviceToken,
                                deviceName = deviceName,
                                linkedAtMillis = container.now(),
                                accountEmail = result.accountEmail,
                                pushTokenHash = PushTokenSync.hash(token),
                            ),
                        )
                        LinkingState(linked = true)
                    }

                    ClaimResult.InvalidCode -> {
                        LinkingState(codeError = true, problem = LinkProblem.InvalidCode)
                    }

                    is ClaimResult.Throttled -> {
                        LinkingState(problem = LinkProblem.Throttled(result.retryAfterSeconds))
                    }

                    ClaimResult.Network -> {
                        LinkingState(problem = LinkProblem.Network)
                    }

                    is ClaimResult.Refused -> {
                        LinkingState(problem = LinkProblem.Refused)
                    }
                }
        }
    }

    private companion object {
        /** Long enough for any phone's name; the console lists it. */
        const val MAX_NAME = 64
    }
}
