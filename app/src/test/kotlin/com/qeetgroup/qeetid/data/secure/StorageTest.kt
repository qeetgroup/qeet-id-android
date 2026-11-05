package com.qeetgroup.qeetid.data.secure

import com.qeetgroup.qeetid.SoftwareSecretBox
import com.qeetgroup.qeetid.approvals.PhoneLink
import com.qeetgroup.qeetid.approvals.PhoneLinkStore
import com.qeetgroup.qeetid.codes.AccountStore
import com.qeetgroup.qeetid.codes.totp.Base32
import com.qeetgroup.qeetid.codes.totp.NewAccount
import com.qeetgroup.qeetid.codes.totp.Totp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class StorageTest {
    @get:Rule val folder = TemporaryFolder()

    private val box = SoftwareSecretBox()
    private val secret = Base32.decode("JBSWY3DPEHPK3PXP")!!

    private fun files(dir: File = folder.root) = SealedFiles(dir, box)

    @Test
    fun aSealedFileOpensOnlyUnderItsOwnName() {
        val files = files()
        files.write("secret-a", "one".toByteArray())
        files.write("secret-b", "two".toByteArray())
        assertArrayEquals("one".toByteArray(), files.read("secret-a"))
        // Swap the files on disk: each is bound to its name, so neither opens.
        val a = File(folder.root, "secret-a")
        val b = File(folder.root, "secret-b")
        val bytes = a.readBytes()
        a.writeBytes(b.readBytes())
        b.writeBytes(bytes)
        assertTrue(runCatching { files.read("secret-a") }.exceptionOrNull() is SealedFiles.Unreadable)
    }

    @Test
    fun valuesAreNotStoredInTheClear() {
        files().write("phone-link", "device-token-123".toByteArray())
        val raw = File(folder.root, "phone-link").readBytes().decodeToString()
        assertFalse(raw.contains("device-token-123"))
    }

    @Test
    fun aMissingFileReadsAsNothing() {
        assertNull(files().read("accounts"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun namesCannotLeaveTheDirectory() {
        files().write("../escape", byteArrayOf(1))
    }

    @Test
    fun accountsAndTheirSecretsAreKeptApart() =
        runBlocking {
            val store = AccountStore(files(), Dispatchers.Unconfined) { 1_000L }
            val added = store.add(NewAccount(" GitHub ", "brightleaf-foods", secret, digits = 6))
            assertEquals("GitHub", added.issuer)
            assertEquals(listOf(added), store.load())
            assertTrue(File(folder.root, "secret-${added.id}").exists())
            val list = File(folder.root, "accounts").readBytes().decodeToString()
            assertFalse("the list holds no secret", list.contains("JBSW"))

            // A second store over the same files reads the same account and the same code.
            val again = AccountStore(files(), Dispatchers.Unconfined)
            val loaded = again.load().single()
            again.warmUp(listOf(loaded))
            assertEquals(Totp.code(secret, 59_000).code, again.code(loaded, 59_000)!!.code)
        }

    @Test
    fun renameAndRemove() =
        runBlocking {
            val store = AccountStore(files(), Dispatchers.Unconfined)
            val a = store.add(NewAccount("GitHub", "brightleaf-foods", secret))
            val b = store.add(NewAccount("AWS", "billing", secret))
            store.rename(a.id, "GitHub (work)", "ops")
            assertEquals(
                "GitHub (work)",
                store.accounts.value!!
                    .first()
                    .issuer,
            )
            store.remove(b.id)
            assertEquals(listOf(a.id), store.accounts.value!!.map { it.id })
            assertFalse(File(folder.root, "secret-${b.id}").exists())
        }

    @Test
    fun forgottenSecretsAreReadAgain() =
        runBlocking {
            val store = AccountStore(files(), Dispatchers.Unconfined)
            val a = store.add(NewAccount("GitHub", "", secret))
            store.warmUp(listOf(a))
            store.forgetSecrets()
            assertEquals(Totp.code(secret, 0).code, store.code(a, 0)!!.code)
        }

    @Test
    fun aListSealedByAnotherKeyIsSetAsideAndSaidSo() =
        runBlocking {
            AccountStore(files(), Dispatchers.Unconfined).add(NewAccount("GitHub", "", secret))
            // Another key (the Keystore was reset, or the files came from another phone).
            val other = AccountStore(SealedFiles(folder.root, SoftwareSecretBox()), Dispatchers.Unconfined)
            assertEquals(emptyList<Any>(), other.load())
            assertTrue(other.unreadable.value)
        }

    @Test
    fun thePhoneLinkKeepsTheCredentialSealedAndShowsOnlyTheSummary() =
        runBlocking {
            val store = PhoneLinkStore(files(), Dispatchers.Unconfined)
            assertNull(store.get())
            val link = PhoneLink("user-1", "device-1", "token-secret", "Meera's Pixel 9", 0L, "meera@brightleaffoods.in")
            store.save(link)
            assertEquals(link, PhoneLinkStore(files(), Dispatchers.Unconfined).get())
            val summary = store.summary.value!!
            assertEquals("Meera's Pixel 9", summary.deviceName)
            assertEquals(TimeUnit.DAYS.toMillis(180), summary.linkedUntilMillis)
            assertFalse(link.toString().contains("token-secret"))
            store.update { it.copy(pushTokenHash = "abc") }
            assertEquals("abc", store.get()!!.pushTokenHash)
            store.clear()
            assertNull(store.get())
            assertNull(store.summary.value)
        }
}
