package com.qeetgroup.qeetid.approvals

import androidx.test.core.app.ApplicationProvider
import com.qeetgroup.qeetid.AppContainer
import com.qeetgroup.qeetid.FakePushTokens
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.TestContainers
import com.qeetgroup.qeetid.approvals.push.PushAvailability
import com.qeetgroup.qeetid.approvals.push.PushMessages
import com.qeetgroup.qeetid.approvals.push.PushTokenSync
import com.qeetgroup.qeetid.awaitFirst
import com.qeetgroup.qeetid.awaitUntil
import com.qeetgroup.qeetid.data.api.PushTokenResult
import com.qeetgroup.qeetid.settings.SettingsViewModel
import com.qeetgroup.qeetid.settings.UnlinkOutcome
import com.qeetgroup.qeetid.ui.NamePhone
import com.qeetgroup.qeetid.ui.RequestsWaiting
import com.qeetgroup.qeetid.ui.SignInRequestRoute
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ApprovalsTest {
    private val server = MockWebServer()
    private lateinit var container: AppContainer
    private val push = FakePushTokens()
    private val requestId = UUID.randomUUID().toString()

    @Before
    fun start() {
        server.start()
        container = TestContainers.create(ApplicationProvider.getApplicationContext(), server.url("/"), push)
    }

    @After
    fun stop() = server.close()

    private fun respond(
        code: Int,
        body: String = "",
    ) = server.enqueue(
        MockResponse
            .Builder()
            .code(code)
            .body(body)
            .build(),
    )

    private fun link(
        userId: String = "u1",
        hash: String? = null,
    ) = runBlocking {
        container.links.save(PhoneLink(userId, "d1", "t1", "Meera's Pixel 9", 0L, "meera@brightleaffoods.in", hash))
    }

    private fun request(
        status: String = "pending",
        userId: String = "u1",
        expires: Instant = Instant.now().plusSeconds(110),
        id: String = requestId,
    ) = """{"id":"$id","user_id":"$userId","action":"sign_in","status":"$status","expires_at":"$expires",
           "created_at":"${Instant.now()}","context":{"ip":"49.205.143.27","user_agent":"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/131.0"}}"""

    // Linking

    @Test
    fun linkingSavesTheCredentialAndTheAccount() {
        respond(201, """{"user_id":"u1","device":{"id":"d1"},"device_token":"t1","account":{"email":"meera@brightleaffoods.in"}}""")
        val model = LinkingViewModel(container)
        model.link("km7qx 4trwp", "  Meera's Pixel 9  ")
        awaitUntil { model.state.value.linked }
        val link = runBlocking { container.links.get() }!!
        assertEquals("t1", link.deviceToken)
        assertEquals("Meera's Pixel 9", link.deviceName)
        assertEquals("meera@brightleaffoods.in", link.accountEmail)
        assertEquals(PushTokenSync.hash("fcm-token-1"), link.pushTokenHash)
        assertTrue(
            server
                .takeRequest()
                .body!!
                .utf8()
                .contains("\"code\":\"KM7QX-4TRWP\""),
        )
    }

    @Test
    fun linkingChecksTheCodeAndNameFirst() {
        val model = LinkingViewModel(container)
        model.link("KM7QX", " ")
        assertTrue(model.state.value.codeError)
        assertTrue(model.state.value.nameError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aWrongCodeIsShownOnTheField() {
        respond(401, """{"error":{"code":"mfa.pairing_code_invalid","message":"x"}}""")
        val model = LinkingViewModel(container)
        model.link("KM7QX-4TRWP", "Pixel")
        awaitUntil { model.state.value.problem != null }
        assertEquals(LinkProblem.InvalidCode, model.state.value.problem)
        assertTrue(model.state.value.codeError)
        assertNull(runBlocking { container.links.get() })
    }

    @Test
    fun noPushTokenMeansNoLinking() {
        push.token = null
        val model = LinkingViewModel(container)
        model.link("KM7QX-4TRWP", "Pixel")
        awaitUntil { model.state.value.problem != null }
        assertEquals(LinkProblem.NoPushToken, model.state.value.problem)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aBuildWithoutPushCannotLink() {
        val none =
            TestContainers.create(
                ApplicationProvider.getApplicationContext(),
                server.url("/"),
                FakePushTokens(PushAvailability.Unavailable),
            )
        val model = LinkingViewModel(none)
        model.link("KM7QX-4TRWP", "Pixel")
        assertEquals(LinkProblem.Unavailable, model.state.value.problem)
    }

    // A sign-in request

    @Test
    fun approvingSendsTheNumber() {
        link()
        respond(200, request())
        val model = RequestViewModel(container, requestId)
        awaitUntil { model.state.value is RequestState.Review }
        respond(200, """{"status":"approved"}""")
        model.approve(47)
        awaitUntil { model.state.value is RequestState.Approved }
        server.takeRequest()
        assertTrue(
            server
                .takeRequest()
                .body!!
                .utf8()
                .contains("\"number\":47"),
        )
    }

    @Test
    fun denying() {
        link()
        respond(200, request())
        val model = RequestViewModel(container, requestId)
        awaitUntil { model.state.value is RequestState.Review }
        respond(200, """{"status":"denied"}""")
        model.deny()
        awaitUntil { model.state.value is RequestState.Denied }
    }

    @Test
    fun aWrongNumberEndsTheRequest() {
        link()
        respond(200, request())
        val model = RequestViewModel(container, requestId)
        awaitUntil { model.state.value is RequestState.Review }
        respond(403, """{"error":{"code":"mfa.push_number_mismatch","message":"x"}}""")
        model.approve(12)
        awaitUntil { model.state.value is RequestState.Ended }
        assertEquals(EndReason.NumberMismatch, (model.state.value as RequestState.Ended).reason)
    }

    @Test
    fun aNetworkFailureKeepsTheRequestOpenAndSaysSo() {
        link()
        respond(200, request())
        val model = RequestViewModel(container, requestId)
        awaitUntil { model.state.value is RequestState.Review }
        respond(503)
        model.approve(47)
        awaitUntil { (model.state.value as? RequestState.Review)?.error != null }
        assertEquals(R.string.request_error_network, (model.state.value as RequestState.Review).error)
    }

    @Test
    fun requestStatesFromTheServer() {
        link()
        val cases: List<Pair<String, (RequestState) -> Boolean>> =
            listOf(
                request(status = "approved") to { it is RequestState.Answered && it.approved },
                request(status = "denied") to { it is RequestState.Answered && !it.approved },
                request(status = "expired") to { it is RequestState.Ended && it.reason == EndReason.Expired && it.request != null },
                request(expires = Instant.now().minusSeconds(5)) to { it is RequestState.Ended && it.reason == EndReason.Expired },
                request(userId = "someone-else") to { it == RequestState.NotThisAccount },
            )
        for ((body, matches) in cases) {
            respond(200, body)
            val model = RequestViewModel(container, requestId)
            awaitUntil { model.state.value != RequestState.Loading }
            assertTrue("$body gave ${model.state.value}", matches(model.state.value))
        }
        respond(404)
        val gone = RequestViewModel(container, requestId)
        awaitUntil { gone.state.value != RequestState.Loading }
        assertEquals(RequestState.Ended(EndReason.Gone), gone.state.value)
        respond(500)
        val failed = RequestViewModel(container, requestId)
        awaitUntil { failed.state.value != RequestState.Loading }
        assertEquals(RequestState.LoadFailed, failed.state.value)
    }

    @Test
    fun anUnlinkedPhoneCannotAnswer() {
        val model = RequestViewModel(container, requestId)
        awaitUntil { model.state.value != RequestState.Loading }
        assertEquals(RequestState.NotLinked, model.state.value)
        assertEquals(0, server.requestCount)
    }

    // Requests waiting for the phone

    @Test
    fun theNewestWaitingRequestIsOpenedOnce() {
        link()
        val pending = """{"challenges":[${request()}]}"""
        respond(200, pending)
        container.inbox.checkPending()
        awaitUntil { container.inbox.next.value != null }
        assertEquals(SignInRequestRoute(requestId), container.inbox.next.value)
        container.inbox.opened(SignInRequestRoute(requestId))
        assertNull(container.inbox.next.value)

        respond(200, pending)
        runBlocking { container.inbox.checkPending().join() }
        assertNull("not opened again on its own", container.inbox.next.value)

        respond(200, pending)
        runBlocking { container.inbox.checkPending(explicit = true).join() }
        assertEquals("a pull to refresh opens it again", SignInRequestRoute(requestId), container.inbox.next.value)
    }

    @Test
    fun moreThanOneWaitingOpensRequestsWaiting() {
        link()
        val pending = """{"challenges":[${request()},${request(id = UUID.randomUUID().toString())}]}"""
        respond(200, pending)
        runBlocking { container.inbox.checkPending().join() }
        assertEquals(RequestsWaiting, container.inbox.next.value)
        container.inbox.opened(RequestsWaiting)
        assertNull(container.inbox.next.value)

        respond(200, pending)
        runBlocking { container.inbox.checkPending().join() }
        assertNull("both were seen", container.inbox.next.value)
    }

    @Test
    fun denyAllDeniesEveryWaitingRequest() {
        link()
        val other = UUID.randomUUID().toString()
        respond(200, """{"challenges":[${request()},${request(id = other)}]}""")
        val model = RequestsWaitingViewModel(container)
        awaitUntil { model.state.value is WaitingState.Waiting }
        assertEquals(2, (model.state.value as WaitingState.Waiting).requests.size)
        server.takeRequest()
        respond(200, """{"status":"denied"}""")
        respond(200, """{"status":"denied"}""")
        model.denyAll()
        awaitUntil { model.state.value is WaitingState.DeniedAll }
        assertEquals(listOf(requestId, other), (model.state.value as WaitingState.DeniedAll).ids)
        repeat(2) {
            val body =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            assertTrue(body, body.contains("\"approved\":false"))
        }
    }

    @Test
    fun aRefusedCredentialForgetsTheLink() {
        link()
        respond(401, """{"error":{"code":"mfa.push_unauthorized","message":"x"}}""")
        runBlocking { container.inbox.checkPending().join() }
        assertNull(runBlocking { container.links.get() })
        assertEquals(1, push.stopped)
    }

    @Test
    fun anOlderServerWithoutTheRouteChangesNothing() {
        link()
        respond(404, "404 page not found")
        runBlocking { container.inbox.checkPending().join() }
        assertNull(container.inbox.next.value)
        assertTrue(runBlocking { container.links.get() } != null)
    }

    @Test
    fun aPairingLinkOpensTheConfirmationNotTheLink() {
        container.inbox.openPairing("qeetidauth://pair?code=km7qx-4trwp")
        assertEquals(NamePhone("KM7QX-4TRWP"), container.inbox.next.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun onlyRequestIdsAreOpened() {
        container.inbox.openRequest("../settings")
        assertNull(container.inbox.next.value)
    }

    // Push

    @Test
    fun pushMessagesCarryARequestId() {
        assertEquals(
            requestId,
            PushMessages.requestId(mapOf("type" to "sign_in_request", "challenge_id" to requestId, "action" to "sign_in")),
        )
        assertEquals(requestId, PushMessages.requestId(mapOf("challenge_id" to requestId, "action" to "sign_in")))
        assertNull(PushMessages.requestId(mapOf("type" to "marketing", "challenge_id" to requestId)))
        assertNull(PushMessages.requestId(mapOf("type" to "sign_in_request", "challenge_id" to "not-a-uuid")))
        assertNull(PushMessages.requestId(emptyMap()))
    }

    @Test
    fun aNewPushTokenIsSentOnceAndRemembered() {
        link(hash = PushTokenSync.hash("fcm-token-1"))
        runBlocking {
            assertNull("the server has this one already", container.pushSync.sync("fcm-token-1"))
            respond(204)
            assertEquals(PushTokenResult.Updated, container.pushSync.sync("fcm-token-2"))
            assertEquals(PushTokenSync.hash("fcm-token-2"), container.links.get()!!.pushTokenHash)
            assertTrue(
                server
                    .takeRequest()
                    .body!!
                    .utf8()
                    .contains("\"push_token\":\"fcm-token-2\""),
            )
            respond(404, "404 page not found")
            assertEquals(PushTokenResult.NotSupported, container.pushSync.sync("fcm-token-3"))
            assertEquals("kept for the next try", PushTokenSync.hash("fcm-token-2"), container.links.get()!!.pushTokenHash)
            respond(401, """{"error":{"code":"mfa.push_unauthorized","message":"x"}}""")
            assertEquals(PushTokenResult.Unauthorized, container.pushSync.sync("fcm-token-3"))
            assertNull(container.links.get())
        }
    }

    // Unlinking

    @Test
    fun unlinkingRemovesThePhoneOnTheServerFirst() {
        link()
        respond(204)
        val model = SettingsViewModel(container)
        model.unlink()
        val outcome = model.unlinkOutcomes.awaitFirst()
        assertEquals(UnlinkOutcome.Unlinked, outcome)
        assertNull(runBlocking { container.links.get() })
        assertEquals(1, push.stopped)
        assertEquals("/v1/mfa/push/devices/d1/unlink", server.takeRequest().url.encodedPath)
    }

    @Test
    fun anUnreachableServerKeepsThePhoneLinked() {
        link()
        respond(503)
        val model = SettingsViewModel(container)
        model.unlink()
        assertEquals(UnlinkOutcome.FailedNetwork, model.unlinkOutcomes.awaitFirst())
        assertTrue(runBlocking { container.links.get() } != null)
        assertEquals(0, push.stopped)
    }

    @Test
    fun aPhoneTheServerAlreadyRemovedIsClearedToo() {
        link()
        respond(401, """{"error":{"code":"mfa.push_unauthorized","message":"x"}}""")
        val model = SettingsViewModel(container)
        model.unlink()
        assertEquals(UnlinkOutcome.AlreadyRemoved, model.unlinkOutcomes.awaitFirst())
        assertNull(runBlocking { container.links.get() })
    }
}
