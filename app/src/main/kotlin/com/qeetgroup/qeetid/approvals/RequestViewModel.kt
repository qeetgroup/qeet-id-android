package com.qeetgroup.qeetid.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qeetgroup.qeetid.AppContainer
import com.qeetgroup.qeetid.data.api.FetchResult
import com.qeetgroup.qeetid.data.api.RespondResult
import com.qeetgroup.qeetid.data.api.SignInRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Why a request can no longer be answered. */
enum class EndReason {
    /** The number did not match: the server refused the sign-in. */
    NumberMismatch,

    /** Two minutes passed. */
    Expired,

    /** Not found: so old it was removed, or never this server's. */
    Gone,
}

sealed interface RequestState {
    data object Loading : RequestState

    /** The request could not be read (offline, or the server failed). */
    data object LoadFailed : RequestState

    /** The phone is not linked, so it cannot answer. */
    data object NotLinked : RequestState

    /** The phone's credential does not answer for the account that sent it. */
    data object NotThisAccount : RequestState

    /** Waiting for an answer: the details, the time left, and whether an answer is on its way. */
    data class Review(
        val request: SignInRequest,
        val secondsLeft: Int,
        val sending: Boolean = false,
        /** A string resource saying why the last answer was not sent, if it was not. */
        val error: Int? = null,
    ) : RequestState

    data class Approved(
        val request: SignInRequest,
    ) : RequestState

    data object Denied : RequestState

    data class Ended(
        val reason: EndReason,
    ) : RequestState

    /** Already approved or denied, on this phone or another. */
    data class Answered(
        val approved: Boolean,
    ) : RequestState
}

/**
 * One sign-in request (Screens 16–20): read it, count down its two minutes by the server's clock, and answer it with
 * the device credential and, to approve, the number from the sign-in screen. The person's fingerprint or PIN is asked
 * for by the screen before [approve] is called.
 */
class RequestViewModel(
    private val container: AppContainer,
    private val id: String,
) : ViewModel() {
    private val _state = MutableStateFlow<RequestState>(RequestState.Loading)
    val state: StateFlow<RequestState> = _state.asStateFlow()
    private var expiresAtLocal = 0L
    private var countdown: Job? = null

    init {
        load()
    }

    fun load() {
        _state.value = RequestState.Loading
        viewModelScope.launch {
            val link = container.links.get()
            if (link == null) {
                _state.value = RequestState.NotLinked
                return@launch
            }
            _state.value =
                when (val result = container.api.signInRequest(id)) {
                    FetchResult.Network -> {
                        RequestState.LoadFailed
                    }

                    FetchResult.NotFound -> {
                        RequestState.Ended(EndReason.Gone)
                    }

                    is FetchResult.Found -> {
                        val request = result.request
                        expiresAtLocal = result.expiresAtLocalMillis
                        when {
                            request.userId.isNotEmpty() && request.userId != link.userId -> RequestState.NotThisAccount
                            request.status == SignInRequest.APPROVED -> RequestState.Answered(approved = true)
                            request.status == SignInRequest.DENIED -> RequestState.Answered(approved = false)
                            request.status != SignInRequest.PENDING || secondsLeft() == 0 -> RequestState.Ended(EndReason.Expired)
                            else -> RequestState.Review(request, secondsLeft()).also { startCountdown() }
                        }
                    }
                }
        }
    }

    /** Approves with [number], after the screen has verified the person. */
    fun approve(number: Int) = respond(approved = true, number = number)

    fun deny() = respond(approved = false, number = null)

    /** The screen could not verify the person: say so, and send nothing. */
    fun notVerified(reason: Int) {
        _state.update { if (it is RequestState.Review) it.copy(sending = false, error = reason) else it }
    }

    private fun respond(
        approved: Boolean,
        number: Int?,
    ) {
        val review = _state.value as? RequestState.Review ?: return
        if (review.sending) return
        _state.value = review.copy(sending = true, error = null)
        viewModelScope.launch {
            val link = container.links.get()
            if (link == null) {
                _state.value = RequestState.NotLinked
                return@launch
            }
            val result = container.api.respond(id, link.userId, link.deviceToken, approved, number)
            _state.value =
                when (result) {
                    RespondResult.Done -> {
                        if (approved) RequestState.Approved(review.request) else RequestState.Denied
                    }

                    RespondResult.NumberMismatch -> {
                        RequestState.Ended(EndReason.NumberMismatch)
                    }

                    RespondResult.Expired -> {
                        RequestState.Ended(EndReason.Expired)
                    }

                    RespondResult.NotThisAccount -> {
                        RequestState.NotThisAccount
                    }

                    RespondResult.AlreadyAnswered -> {
                        load()
                        return@launch
                    }

                    RespondResult.Network -> {
                        review.copy(sending = false, error = com.qeetgroup.qeetid.R.string.request_error_network)
                    }

                    is RespondResult.Refused -> {
                        review.copy(sending = false, error = com.qeetgroup.qeetid.R.string.request_error_refused)
                    }
                }
            if (_state.value !is RequestState.Review) countdown?.cancel()
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown =
            viewModelScope.launch {
                while (isActive) {
                    delay(TICK_MILLIS - container.now() % TICK_MILLIS)
                    val left = secondsLeft()
                    _state.update { current ->
                        when {
                            current !is RequestState.Review -> current
                            left == 0 && !current.sending -> RequestState.Ended(EndReason.Expired)
                            else -> current.copy(secondsLeft = left)
                        }
                    }
                    if (_state.value !is RequestState.Review) break
                }
            }
    }

    private fun secondsLeft(): Int = ((expiresAtLocal - container.now() + 999) / 1000).coerceAtLeast(0).toInt()

    companion object {
        private const val TICK_MILLIS = 1_000L

        /** When the request was made, for its Time row: the context's timestamp, else its creation. */
        fun requestedAtMillis(request: SignInRequest): Long? =
            listOfNotNull(request.context.timestamp, request.createdAt).firstNotNullOfOrNull {
                try {
                    OffsetDateTime.parse(it).toInstant().toEpochMilli()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
    }
}
