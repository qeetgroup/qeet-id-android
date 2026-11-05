package com.qeetgroup.qeetid.ui

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.qeetgroup.qeetid.AppContainer
import com.qeetgroup.qeetid.FakeConnectivity
import com.qeetgroup.qeetid.FakePushTokens
import com.qeetgroup.qeetid.FakeVerification
import com.qeetgroup.qeetid.TestContainers
import com.qeetgroup.qeetid.approvals.PhoneLink
import com.qeetgroup.qeetid.codes.totp.Base32
import com.qeetgroup.qeetid.codes.totp.NewAccount
import com.qeetgroup.qeetid.lock.VerifyResult
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.util.UUID

/** Every flow of the app, driven through its screens on the JVM, with the server faked. */
@RunWith(RobolectricTestRunner::class)
class AppFlowsTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private val verification = FakeVerification()
    private val connectivity = FakeConnectivity()
    private lateinit var container: AppContainer
    private val secret = Base32.decode("JBSWY3DPEHPK3PXP")!!
    private val requestId = UUID.randomUUID().toString()

    @Before
    fun start() {
        // Reduced motion: Qeetrix's countdowns tick once a second instead of every frame, so the UI can go idle.
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        server.start()
        container = TestContainers.create(context, server.url("/"), FakePushTokens(), connectivity)
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

    private fun launch(onboarded: Boolean = true) {
        container.preferences.setOnboarded(onboarded)
        compose.setContent { QeetIdApp(container, verification) }
    }

    private fun addAccount(
        issuer: String = "GitHub",
        label: String = "brightleaf-foods",
    ) = runBlocking { container.accounts.add(NewAccount(issuer, label, secret)) }

    private fun linkPhone() =
        runBlocking {
            container.links.save(PhoneLink("u1", "d1", "t1", "Meera's Pixel 9", System.currentTimeMillis(), "meera@brightleaffoods.in"))
        }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }

    private fun textFields() = compose.onAllNodes(hasSetTextAction())

    // Getting started

    @Test
    fun welcomeLockAndNotificationsLeadToTheAccounts() {
        launch(onboarded = false)
        compose.onNodeWithText("Sign in safely, with this phone").assertExists()
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Lock Qeet ID").assertExists()
        compose.onNodeWithText("Step 1 of 2").assertExists()
        compose.onNodeWithText("Continue").performClick()
        waitFor("Get sign-in requests on this phone")
        assertEquals("turning the lock on asks first", 1, verification.prompts.size)
        assertTrue(container.preferences.appLock.value)
        compose.onNodeWithText("Only sign-in requests. Nothing else, no marketing.").assertExists()
        compose.onNodeWithText("Not now").performClick()
        waitFor("No accounts yet")
        assertTrue(container.preferences.onboarded.value)
    }

    @Test
    fun notNowLeavesTheLockOff() {
        launch(onboarded = false)
        compose.onNodeWithText("Get started").performClick()
        compose.onNodeWithText("Not now").performClick()
        waitFor("Get sign-in requests on this phone")
        assertEquals(false, container.preferences.appLock.value)
        assertTrue(verification.prompts.isEmpty())
    }

    // Locked

    @Test
    fun lockedUntilVerified() {
        addAccount()
        container.preferences.setAppLock(true)
        container.lock.lock()
        verification.result = VerifyResult.Cancelled
        launch()
        waitFor("Qeet ID is locked")
        compose.onNodeWithText("GitHub").assertDoesNotExist()
        verification.result = VerifyResult.Verified
        compose.onNodeWithText("Unlock").performClick()
        waitFor("GitHub")
        compose.onNodeWithText("Qeet ID is locked").assertDoesNotExist()
    }

    // Sign-in codes

    @Test
    fun accountsShowTheirCodes() {
        addAccount()
        addAccount("AWS", "billing@brightleaffoods.in")
        launch()
        waitFor("GitHub")
        compose.onNodeWithText("2 on this phone").assertExists()
        compose.onNodeWithText("brightleaf-foods").assertExists()
        compose.onNodeWithText("Codes").assertExists()
        compose.onNodeWithText("Sign-in approvals").assertExists()
        compose.onNodeWithText("Not set up · Link this phone").assertExists()
    }

    @Test
    fun typingASetupKeyAddsAnAccount() {
        launch()
        waitFor("No accounts yet")
        compose.onNodeWithText("Type the key").performClick()
        waitFor("Type the setup key")
        textFields()[0].performTextInput("Razorpay")
        textFields()[1].performTextInput("accounts@brightleaffoods.in")
        textFields()[2].performTextInput("jbsw y3dp ehpk 3pxp")
        compose.onNodeWithText("Add account").performClick()
        waitFor("Razorpay added")
        compose.onNodeWithText("accounts@brightleaffoods.in").assertExists()
        assertEquals(
            "Razorpay",
            container.accounts.accounts.value!!
                .single()
                .issuer,
        )
    }

    @Test
    fun aBadSetupKeySaysWhatToFix() {
        launch()
        waitFor("No accounts yet")
        compose.onNodeWithText("Type the key").performClick()
        textFields()[2].performTextInput("JBSW1")
        compose.onNodeWithText("Add account").performClick()
        waitFor("Enter the service's name")
        waitFor("A setup key has only the letters A to Z")
        assertTrue(
            container.accounts.accounts.value
                .orEmpty()
                .isEmpty(),
        )
    }

    @Test
    fun deletingAnAccountAsksFirst() {
        val account = addAccount()
        launch()
        waitFor("GitHub")
        compose.onNodeWithTag("account-${account.id}").performTouchInput { longClick() }
        waitFor("Copy code")
        compose.onNodeWithText("Rename").assertExists()
        compose.onNodeWithText("Delete account").performClick()
        waitFor("Delete GitHub?")
        compose.onNodeWithText("Turn off 2-step verification on GitHub first", substring = true).assertExists()
        compose.onAllNodesWithText("Delete account").onFirst().performClick()
        waitFor("GitHub deleted")
        compose.waitUntil(5_000) {
            container.accounts.accounts.value
                .orEmpty()
                .isEmpty()
        }
    }

    @Test
    fun renamingChangesOnlyTheLabelHere() {
        val account = addAccount()
        launch()
        waitFor("GitHub")
        compose.onNodeWithTag("account-${account.id}").performTouchInput { longClick() }
        waitFor("Rename")
        compose.onNodeWithText("Rename").performClick()
        waitFor("Rename account")
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))[0].performTextReplacement("GitHub (work)")
        compose.onNodeWithText("Save").performClick()
        waitFor("GitHub (work)")
        assertEquals(
            "GitHub (work)",
            container.accounts.accounts.value!!
                .single()
                .issuer,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun theScannerAsksForTheCameraAndOffersTheTypedKey() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
        addAccount()
        launch()
        waitFor("GitHub")
        compose.onNodeWithContentDescription("Add account").performClick()
        waitFor("Allow the camera")
        compose.onNodeWithText("Type the key instead").performClick()
        waitFor("Type the setup key")
    }

    @Test
    fun problemsThatStopRequestsAreExplained() {
        linkPhone()
        addAccount()
        connectivity.online.value = false
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        launch()
        waitFor("You're offline")
        compose.onNodeWithText("Sign-in requests are off").assertExists()
        compose.onNodeWithText("Allow notifications").assertExists()
        compose.onNodeWithText("Linked · Meera's Pixel 9").assertExists()
    }

    // Linking

    @Test
    fun linkingWithATypedCode() {
        respond(201, """{"user_id":"u1","device":{"id":"d1"},"device_token":"t1","account":{"email":"meera@brightleaffoods.in"}}""")
        launch()
        waitFor("No accounts yet")
        compose.onNodeWithText("Link this phone").performClick()
        waitFor("Open Qeet ID on your computer")
        compose.onNodeWithText("Type the code instead").performClick()
        waitFor("Name for this phone")
        textFields()[0].performTextInput("km7qx4trwp")
        textFields()[1].performTextReplacement("Meera's Pixel 9")
        compose.onNodeWithText("Link phone").performClick()
        waitFor("Phone linked")
        compose.onNodeWithText("meera@brightleaffoods.in").assertExists()
        compose.onNodeWithText("Meera's Pixel 9").assertExists()
        compose.onNodeWithText("link again to extend", substring = true).assertExists()
        assertEquals("t1", runBlocking { container.links.get() }!!.deviceToken)
        compose.onNodeWithText("Done").performClick()
        waitFor("No accounts yet")
    }

    @Test
    fun aRefusedCodeSaysSo() {
        respond(401, """{"error":{"code":"mfa.pairing_code_invalid","message":"x"}}""")
        launch()
        container.inbox.openPairing("qeetidauth://pair?code=KM7QX-4TRWP")
        waitFor("Name for this phone")
        compose.onNodeWithText("KM7QX-4TRWP").assertExists()
        compose.onNodeWithText("Link phone").performClick()
        waitFor("That code is wrong, already used or expired")
        assertNull(runBlocking { container.links.get() })
    }

    // Sign-in requests

    private fun request() =
        """{"id":"$requestId","user_id":"u1","action":"sign_in","status":"pending","expires_at":"${Instant.now().plusSeconds(106)}",
           "created_at":"${Instant.now()}","context":{"ip":"49.205.143.27","location":"Hyderabad, India",
           "user_agent":"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"}}"""

    private fun openRequest() {
        linkPhone()
        respond(200, request())
        launch()
        container.inbox.openRequest(requestId)
        waitFor("Are you signing in to Qeet ID?")
        compose.onNodeWithText("Chrome on macOS").assertExists()
        compose.onNodeWithText("Hyderabad, India").assertExists()
        compose.onNodeWithText("49.205.143.27").assertExists()
        compose.onNodeWithText("left", substring = true).assertExists()
    }

    @Test
    fun approvingTakesTheNumberAndTheOwnersFingerprint() {
        openRequest()
        respond(200, """{"status":"approved"}""")
        textFields()[0].performTextInput("47")
        compose.onNodeWithText("Approve").performClick()
        waitFor("Sign-in approved")
        compose.onNodeWithText("Chrome on macOS is signing in to Qeet ID", substring = true).assertExists()
        assertEquals("Approve sign-in", verification.prompts.single().title)
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
    fun noApprovalWithoutTheOwner() {
        openRequest()
        verification.result = VerifyResult.Failed
        textFields()[0].performTextInput("47")
        compose.onNodeWithText("Approve").performClick()
        waitFor("Couldn't confirm it's you, so nothing was sent")
        assertEquals("only the request was read", 1, server.requestCount)
    }

    @Test
    fun denyingBlocksTheSignIn() {
        openRequest()
        respond(200, """{"status":"denied"}""")
        compose.onNodeWithText("Deny").performClick()
        waitFor("Sign-in blocked")
        compose.onNodeWithText("Change your Qeet ID password").assertExists()
        compose.onNodeWithText("Open security").assertExists()
    }

    @Test
    fun aWrongNumberEndsTheRequest() {
        openRequest()
        respond(403, """{"error":{"code":"mfa.push_number_mismatch","message":"x"}}""")
        textFields()[0].performTextInput("12")
        compose.onNodeWithText("Approve").performClick()
        waitFor("This request has ended")
        compose.onNodeWithText("The number didn't match", substring = true).assertExists()
    }

    @Test
    fun aRequestThatCannotBeLoadedCanBeTriedAgain() {
        linkPhone()
        respond(500)
        launch()
        container.inbox.openRequest(requestId)
        waitFor("Couldn't load the request")
        respond(200, request())
        compose.onNodeWithText("Try again").performClick()
        waitFor("Are you signing in to Qeet ID?")
    }

    // Settings

    @Test
    fun settingsShowTheLinkAndUnlinkIt() {
        linkPhone()
        respond(204)
        launch()
        waitFor("Accounts")
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("meera@brightleaffoods.in")
        compose.onNodeWithText("Meera's Pixel 9 · linked until", substring = true).assertExists()
        compose.onNodeWithText("Unlink this phone").performClick()
        waitFor("Unlink this phone?")
        compose.onNodeWithText("Your sign-in codes stay on this phone", substring = true).assertExists()
        compose.onNodeWithText("Unlink").performClick()
        waitFor("Phone unlinked")
        assertNull(runBlocking { container.links.get() })
        compose.onNodeWithText("Off").assertExists()
    }

    @Test
    fun hideCodesAndAppLockSwitches() {
        launch()
        waitFor("Accounts")
        compose.onNodeWithContentDescription("Settings").performClick()
        waitFor("Hide codes until tapped")
        compose.onNodeWithText("Hide codes until tapped").performClick()
        compose.waitUntil(5_000) { container.preferences.hideCodes.value }
        compose.onNodeWithText("App lock").performClick()
        compose.waitUntil(5_000) { container.preferences.appLock.value }
        assertEquals("Turn on app lock", verification.prompts.single().title)
        compose.onNodeWithText("Help and privacy").assertExists()
    }
}
