package com.qeetgroup.qeetid.data.api

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class QeetIdApiTest {
    private val server = MockWebServer()
    private lateinit var api: QeetIdApi
    private val now = 1_800_000_000_000L

    @Before
    fun start() {
        server.start()
        api = QeetIdApi(server.url("/"), now = { now })
    }

    @After
    fun stop() = server.close()

    private fun respond(
        code: Int,
        body: String = "",
        date: Long? = null,
    ) {
        val builder =
            MockResponse
                .Builder()
                .code(code)
                .body(body)
                .addHeader("Content-Type", "application/json")
        if (date != null) {
            builder.addHeader("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochMilli(date).atZone(ZoneOffset.UTC)))
        }
        server.enqueue(builder.build())
    }

    private fun error(
        code: String,
        metadata: String = "{}",
    ) = """{"error":{"code":"$code","message":"Nope","metadata":$metadata}}"""

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun claimSendsTheCodeAndPushTokenWithNoCredential() =
        runBlocking {
            respond(
                201,
                """{"user_id":"u1","device":{"id":"d1","name":"Pixel","platform":"android"},"device_token":"t1","account":{"email":"meera@brightleaffoods.in"}}""",
            )
            val result = api.claimPairing("KM7QX-4TRWP", "Meera's Pixel 9", "fcm-1")
            assertEquals(ClaimResult.Linked("u1", "d1", "t1", "meera@brightleaffoods.in"), result)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/v1/mfa/push/pairing/claim", request.url.encodedPath)
            assertNull(request.headers["Authorization"])
            assertNull(request.headers["Cookie"])
            val body = json(request.body!!.utf8())
            assertEquals("KM7QX-4TRWP", body["code"]!!.jsonPrimitive.content)
            assertEquals("Meera's Pixel 9", body["name"]!!.jsonPrimitive.content)
            assertEquals("fcm-1", body["push_token"]!!.jsonPrimitive.content)
            assertEquals("android", body["platform"]!!.jsonPrimitive.content)
        }

    @Test
    fun claimWithoutTheAccountStillLinks() =
        runBlocking {
            respond(201, """{"user_id":"u1","device":{"id":"d1"},"device_token":"t1"}""")
            assertEquals(ClaimResult.Linked("u1", "d1", "t1", null), api.claimPairing("KM7QX-4TRWP", "P", "x"))
        }

    @Test
    fun claimFailures() =
        runBlocking {
            respond(401, error("mfa.pairing_code_invalid"))
            assertEquals(ClaimResult.InvalidCode, api.claimPairing("KM7QX-4TRWP", "P", "x"))
            respond(429, error("too_many_requests", """{"retry_after":42}"""))
            assertEquals(ClaimResult.Throttled(42), api.claimPairing("KM7QX-4TRWP", "P", "x"))
            respond(429, "")
            assertEquals(ClaimResult.Throttled(60), api.claimPairing("KM7QX-4TRWP", "P", "x"))
            respond(400, error("bad_request"))
            assertEquals(ClaimResult.Refused("Nope"), api.claimPairing("KM7QX-4TRWP", "P", "x"))
            respond(201, """{"user_id":"","device":{"id":"d1"},"device_token":"t1"}""")
            assertTrue(api.claimPairing("KM7QX-4TRWP", "P", "x") is ClaimResult.Refused)
        }

    @Test
    fun anUnreachableServerIsANetworkFailure() =
        runBlocking {
            val offline = QeetIdApi("http://127.0.0.1:9/".toHttpUrl())
            assertEquals(ClaimResult.Network, offline.claimPairing("KM7QX-4TRWP", "P", "x"))
            assertEquals(FetchResult.Network, offline.signInRequest("c1"))
            assertEquals(RespondResult.Network, offline.respond("c1", "u", "t", true, 47))
            assertEquals(UnlinkResult.Network, offline.unlink("d", "u", "t"))
            assertEquals(PushTokenResult.Network, offline.updatePushToken("d", "u", "t", "p"))
            assertEquals(PendingResult.Network, offline.pendingRequests("d", "u", "t"))
        }

    private val challenge =
        """{"id":"c1","user_id":"u1","action":"sign_in","status":"pending","expires_at":"2027-01-15T08:02:00.123456Z",
           "created_at":"2027-01-15T08:00:00Z","context":{"ip":"49.205.143.27","user_agent":"Chrome","timestamp":"2027-01-15T08:00:00Z"}}"""

    @Test
    fun aRequestsExpiryFollowsTheServersClock() =
        runBlocking {
            val expires = Instant.parse("2027-01-15T08:02:00.123456Z").toEpochMilli()
            // The server's clock is 10 s ahead of the phone's.
            respond(200, challenge, date = now + 10_000)
            val found = api.signInRequest("c1") as FetchResult.Found
            assertEquals("49.205.143.27", found.request.context.ip)
            assertEquals(expires - 10_000, found.expiresAtLocalMillis)
            assertEquals("/v1/mfa/push/challenges/c1", server.takeRequest().url.encodedPath)
        }

    @Test
    fun requestFailures() =
        runBlocking {
            respond(404, error("not_found"))
            assertEquals(FetchResult.NotFound, api.signInRequest("c1"))
            respond(500, "")
            assertEquals(FetchResult.Network, api.signInRequest("c1"))
            respond(200, "not json")
            assertEquals(FetchResult.Network, api.signInRequest("c1"))
        }

    @Test
    fun approvingSendsTheNumberAndDenyingDoesNot() =
        runBlocking {
            respond(200, """{"status":"approved"}""")
            assertEquals(RespondResult.Done, api.respond("c1", "u1", "t1", approved = true, number = 47))
            val approve = server.takeRequest()
            assertEquals("/v1/mfa/push/challenges/c1/respond", approve.url.encodedPath)
            val body = json(approve.body!!.utf8())
            assertEquals("u1", body["user_id"]!!.jsonPrimitive.content)
            assertEquals("t1", body["device_token"]!!.jsonPrimitive.content)
            assertEquals(47, body["number"]!!.jsonPrimitive.int)
            assertNull(approve.headers["Authorization"])

            respond(200, """{"status":"denied"}""")
            assertEquals(RespondResult.Done, api.respond("c1", "u1", "t1", approved = false, number = 47))
            assertFalse(json(server.takeRequest().body!!.utf8()).containsKey("number"))
        }

    @Test
    fun answerFailures() =
        runBlocking {
            respond(403, error("mfa.push_number_mismatch"))
            assertEquals(RespondResult.NumberMismatch, api.respond("c1", "u", "t", true, 12))
            respond(410, error("mfa.push_challenge_expired"))
            assertEquals(RespondResult.Expired, api.respond("c1", "u", "t", true, 12))
            respond(404, error("not_found"))
            assertEquals(RespondResult.Expired, api.respond("c1", "u", "t", true, 12))
            respond(409, error("mfa.push_challenge_invalid"))
            assertEquals(RespondResult.AlreadyAnswered, api.respond("c1", "u", "t", true, 12))
            respond(401, error("mfa.push_unauthorized"))
            assertEquals(RespondResult.NotThisAccount, api.respond("c1", "u", "t", true, 12))
            respond(503, "")
            assertEquals(RespondResult.Network, api.respond("c1", "u", "t", true, 12))
            respond(400, error("bad_request"))
            assertEquals(RespondResult.Refused("Nope"), api.respond("c1", "u", "t", true, 12))
        }

    @Test
    fun unlinking() =
        runBlocking {
            respond(204)
            assertEquals(UnlinkResult.Removed, api.unlink("d1", "u1", "t1"))
            val request = server.takeRequest()
            assertEquals("/v1/mfa/push/devices/d1/unlink", request.url.encodedPath)
            assertEquals("t1", json(request.body!!.utf8())["device_token"]!!.jsonPrimitive.content)
            respond(401, error("mfa.push_unauthorized"))
            assertEquals(UnlinkResult.AlreadyRemoved, api.unlink("d1", "u1", "t1"))
            respond(500, "")
            assertEquals(UnlinkResult.Network, api.unlink("d1", "u1", "t1"))
            respond(400, error("bad_request"))
            assertEquals(UnlinkResult.Refused("Nope"), api.unlink("d1", "u1", "t1"))
        }

    @Test
    fun pushTokenUpdateToleratesAServerWithoutTheRoute() =
        runBlocking {
            respond(204)
            assertEquals(PushTokenResult.Updated, api.updatePushToken("d1", "u1", "t1", "fcm-2"))
            val request = server.takeRequest()
            assertEquals("/v1/mfa/push/devices/d1/push-token", request.url.encodedPath)
            assertEquals("fcm-2", json(request.body!!.utf8())["push_token"]!!.jsonPrimitive.content)
            respond(404, "404 page not found")
            assertEquals(PushTokenResult.NotSupported, api.updatePushToken("d1", "u1", "t1", "fcm-2"))
            respond(401, error("mfa.push_unauthorized"))
            assertEquals(PushTokenResult.Unauthorized, api.updatePushToken("d1", "u1", "t1", "fcm-2"))
            // A 401 that isn't Qeet ID's (a proxy, a gateway) must not make the phone forget its link.
            respond(401, "Unauthorized")
            assertNotEquals(PushTokenResult.Unauthorized, api.updatePushToken("d1", "u1", "t1", "fcm-2"))
        }

    @Test
    fun pendingRequests() =
        runBlocking {
            respond(
                200,
                """{"challenges":[{"id":"c2","action":"sign_in","status":"pending","expires_at":"2027-01-15T08:02:00Z","created_at":"2027-01-15T08:00:00Z","context":{}}]}""",
            )
            val found = api.pendingRequests("d1", "u1", "t1") as PendingResult.Found
            assertEquals(listOf("c2"), found.requests.map { it.id })
            assertEquals("/v1/mfa/push/devices/d1/pending", server.takeRequest().url.encodedPath)
            respond(404, "404 page not found")
            assertEquals(PendingResult.NotSupported, api.pendingRequests("d1", "u1", "t1"))
            respond(401, error("mfa.push_unauthorized"))
            assertEquals(PendingResult.Unauthorized, api.pendingRequests("d1", "u1", "t1"))
            respond(401, "Unauthorized")
            assertEquals(PendingResult.Network, api.pendingRequests("d1", "u1", "t1"))
        }

    @Test
    fun anIdCannotEscapeItsPathSegment() =
        runBlocking {
            respond(404, "")
            api.signInRequest("../../admin")
            assertEquals("/v1/mfa/push/challenges/..%2F..%2Fadmin", server.takeRequest().url.encodedPath)
        }
}
