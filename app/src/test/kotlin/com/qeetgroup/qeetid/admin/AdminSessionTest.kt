package com.qeetgroup.qeetid.admin

import androidx.test.core.app.ApplicationProvider
import com.qeetgroup.qeetid.AppContainer
import com.qeetgroup.qeetid.SoftwareSecretBox
import com.qeetgroup.qeetid.TestContainers
import com.qeetgroup.qeetid.data.secure.SealedFiles
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** A fake passkey prompt: signs every request, and remembers what it was asked. */
class FakePasskeys(
    var result: PasskeyResult = PasskeyResult.Signed("""{"id":"cred","type":"public-key","response":{}}"""),
) : PasskeyPrompt {
    val asked = CopyOnWriteArrayList<String>()

    override suspend fun sign(requestJson: String): PasskeyResult {
        asked += requestJson
        return result
    }
}

/**
 * A Qeet ID stand-in for the administration routes, by method and path. Access tokens are `a1`, `a2`…; the
 * refresh token `r1` renews once into `a2`/`r2`; [expired] makes `a1` answer 401.
 */
class FakeQeetId : Dispatcher() {
    val calls = CopyOnWriteArrayList<String>()
    var organizations = """[{"id":"t1","name":"Brightleaf Foods","roles":["Administrator"]}]"""
    var expired = false
    var refreshRefused = false
    var stepUpNeeded = true
    private var steppedUp = false

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val auth = request.headers["Authorization"]
        calls += "${request.method} $path"

        fun ok(body: String) =
            MockResponse
                .Builder()
                .code(200)
                .body(body)
                .build()

        fun status(
            code: Int,
            error: String? = null,
        ) = MockResponse
            .Builder()
            .code(code)
            .body(error?.let { """{"error":{"code":"$it","message":"x"}}""" } ?: "")
            .build()
        val signedIn = auth == "Bearer a2" || (auth == "Bearer a1" && !expired)
        return when {
            path == "/v1/passkeys/login/begin" -> {
                ok("""{"session_id":"c1","publicKey":{"challenge":"abc","rpId":"id.qeet.in"}}""")
            }

            path == "/v1/passkeys/login/finish" -> {
                ok("""{"access_token":"a1","refresh_token":"r1","tenant_id":"home"}""")
            }

            path == "/v1/auth/refresh" -> {
                if (refreshRefused) status(401, "auth.session_revoked") else ok("""{"access_token":"a2","refresh_token":"r2"}""")
            }

            !signedIn -> {
                status(401, "unauthorized")
            }

            path == "/v1/me" -> {
                ok("""{"email":"meera@brightleaffoods.in"}""")
            }

            path == "/v1/me/organizations" -> {
                ok("""{"items":$organizations,"next_cursor":""}""")
            }

            path == "/v1/auth/switch-tenant" -> {
                ok("""{"access_token":"a2","refresh_token":"r2","tenant_id":"t1"}""")
            }

            path == "/v1/auth/session" -> {
                status(204)
            }

            path == "/v1/mfa/webauthn/challenge" -> {
                ok("""{"session_id":"s1","publicKey":{"challenge":"def"}}""")
            }

            path == "/v1/mfa/webauthn/verify" -> {
                ok("""{"status":"verified"}""").also { steppedUp = true }
            }

            path == "/v1/tenants/t1/access-requests" -> {
                ok(
                    """{"pending":1,"access_requests":[{"id":"q1","requester":{"id":"u2","email":"arjun@brightleaffoods.in","name":"Arjun Rao"},
                       "role":{"id":"r1","name":"Finance administrator"},"reason":"Closing the quarter","duration_hours":168,
                       "status":"pending","created_at":"2026-11-08T08:00:00Z"}]}""",
                )
            }

            path == "/v1/tenants/t1/access-requests/q1" -> {
                ok(
                    """{"access_request":{"id":"q1","requester":{"id":"u2","email":"arjun@brightleaffoods.in","name":"Arjun Rao"},
                       "role":{"id":"r1","name":"Finance administrator","description":"Approve payments, change payees"},
                       "reason":"Closing the quarter","duration_hours":168,"status":"pending","created_at":"2026-11-08T08:00:00Z"}}""",
                )
            }

            path == "/v1/tenants/t1/access-requests/q1/approve" -> {
                if (stepUpNeeded && !steppedUp) {
                    status(403, "step_up_required")
                } else {
                    ok(
                        """{"access_request":{"id":"q1","requester":{"id":"u2","email":"arjun@brightleaffoods.in","name":"Arjun Rao"},
                           "role":{"id":"r1","name":"Finance administrator"},"status":"approved","created_at":"2026-11-08T08:00:00Z"}}""",
                    )
                }
            }

            path == "/v1/tenants/t1/critical-events" -> {
                ok(
                    """{"hours":24,"high":1,"events":[{"id":"e1","source":"audit","level":"high","action":"user.suspended","occurred_at":"2026-11-08T08:00:00Z"}]}""",
                )
            }

            path == "/v1/tenants/t1/agents" -> {
                ok("""{"items":[{"id":"g1","disabled":false},{"id":"g2","disabled":true}]}""")
            }

            path == "/v1/tenants/t1/agents/kill-all" -> {
                ok("""{"suspended":1}""")
            }

            else -> {
                status(404)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
class AdminSessionTest {
    private val server = MockWebServer()
    private val qeetId = FakeQeetId()
    private val passkeys = FakePasskeys()
    private lateinit var container: AppContainer

    @Before
    fun start() {
        server.dispatcher = qeetId
        server.start()
        container = TestContainers.create(ApplicationProvider.getApplicationContext(), server.url("/"))
    }

    @After
    fun stop() = server.close()

    private val admin get() = container.admin

    private fun signIn(): SignInOutcome = runBlocking { admin.signIn(passkeys) }

    @Test
    fun aPasskeySignInListsTheOrganisationsThePersonAdministers() {
        qeetId.organizations = """[{"id":"t1","name":"Brightleaf Foods","roles":["Administrator"]},{"id":"t2","name":"Own","roles":[]}]"""
        val outcome = signIn()
        assertEquals(listOf("t1"), (outcome as SignInOutcome.SignedIn).organizations.map { it.id })
        assertEquals("""{"challenge":"abc","rpId":"id.qeet.in"}""", passkeys.asked.single())
        assertEquals("meera@brightleaffoods.in", admin.summary.value?.email)
        assertNull("no organisation chosen yet", admin.summary.value?.organizationId)
    }

    @Test
    fun choosingAnOrganisationScopesTheSession() {
        val organization = (signIn() as SignInOutcome.SignedIn).organizations.single()
        assertTrue(runBlocking { admin.choose(organization) } is AdminResult.Ok)
        assertEquals("Brightleaf Foods", admin.summary.value?.organizationName)
        val requests = runBlocking { admin.inOrganization { access, tenant -> admin.api.accessRequests(access, tenant) } }
        assertEquals(1, (requests as AdminResult.Ok).value.pending)
    }

    @Test
    fun someoneWhoAdministersNothingIsSignedOutAgain() {
        qeetId.organizations = """[{"id":"t2","name":"Own","roles":[]}]"""
        assertEquals(SignInOutcome.NotAnAdministrator, signIn())
        assertNull(admin.summary.value)
        assertTrue("DELETE /v1/auth/session" in qeetId.calls)
    }

    @Test
    fun aCancelledPasskeyChangesNothing() {
        passkeys.result = PasskeyResult.Cancelled
        assertEquals(SignInOutcome.Cancelled, signIn())
        assertNull(admin.summary.value)
        assertTrue("POST /v1/passkeys/login/finish" !in qeetId.calls)
    }

    @Test
    fun anExpiredAccessTokenIsRenewedOnce() {
        signIn()
        qeetId.expired = true
        val organizations = runBlocking { admin.organizations() }
        assertTrue(organizations is AdminResult.Ok)
        assertTrue("POST /v1/auth/refresh" in qeetId.calls)
    }

    @Test
    fun aSessionQeetIdEndedIsForgotten() {
        signIn()
        qeetId.expired = true
        qeetId.refreshRefused = true
        assertEquals(AdminResult.SignedOut, runBlocking { admin.organizations() })
        assertNull(admin.summary.value)
    }

    @Test
    fun approvingNeedsAFreshPasskeyCheck() {
        val organization = (signIn() as SignInOutcome.SignedIn).organizations.single()
        runBlocking { admin.choose(organization) }
        val before = runBlocking { admin.inOrganization { access, tenant -> admin.api.approve(access, tenant, "q1", "") } }
        assertEquals(AdminResult.StepUpRequired, before)
        assertEquals(StepUpOutcome.Done, runBlocking { admin.stepUp(passkeys) })
        val after = runBlocking { admin.inOrganization { access, tenant -> admin.api.approve(access, tenant, "q1", "") } }
        assertEquals("approved", (after as AdminResult.Ok).value.status)
    }

    @Test
    fun theSessionSurvivesARestartSealed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val files = SealedFiles(File(context.cacheDir, "admin-${UUID.randomUUID()}"), SoftwareSecretBox())
        val first = AdminSession(files, admin.api)
        val organization = (runBlocking { first.signIn(passkeys) } as SignInOutcome.SignedIn).organizations.single()
        runBlocking { first.choose(organization) }
        val fresh = AdminSession(files, admin.api)
        runBlocking { fresh.load() }
        assertEquals("Brightleaf Foods", fresh.summary.value?.organizationName)
        // The access token isn't kept: the first call renews it from the sealed refresh token.
        assertTrue(runBlocking { fresh.organizations() } is AdminResult.Ok)
    }

    @Test
    fun countingAndStoppingAgents() {
        val organization = (signIn() as SignInOutcome.SignedIn).organizations.single()
        runBlocking { admin.choose(organization) }
        assertEquals(AdminResult.Ok(1), runBlocking { admin.inOrganization { access, tenant -> admin.api.runningAgents(access, tenant) } })
        assertEquals(AdminResult.Ok(1), runBlocking { admin.inOrganization { access, tenant -> admin.api.stopAllAgents(access, tenant) } })
    }

    @Test
    fun signingOutForgetsTheSession() {
        signIn()
        runBlocking { admin.signOut() }
        assertNull(admin.summary.value)
        assertEquals(AdminResult.SignedOut, runBlocking { admin.organizations() })
    }
}
