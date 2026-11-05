package com.qeetgroup.qeetid.codes

import androidx.test.core.app.ApplicationProvider
import com.qeetgroup.qeetid.TestContainers
import com.qeetgroup.qeetid.awaitFirst
import com.qeetgroup.qeetid.awaitUntil
import com.qeetgroup.qeetid.codes.totp.Base32
import com.qeetgroup.qeetid.codes.totp.NewAccount
import com.qeetgroup.qeetid.codes.totp.Totp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountsViewModelTest {
    private val now = 59_000L
    private val secret = Base32.decode("JBSWY3DPEHPK3PXP")!!
    private val container = TestContainers.create(ApplicationProvider.getApplicationContext(), now = { now })

    private fun model(): AccountsViewModel {
        runBlocking {
            container.accounts.add(NewAccount("GitHub", "brightleaf-foods", secret))
            container.accounts.add(NewAccount("AWS", "billing@brightleaffoods.in", secret, digits = 8))
        }
        val model = AccountsViewModel(container)
        // The state is shared while someone watches it, as the screen does.
        CoroutineScope(Dispatchers.Main).launch { model.state.collect {} }
        awaitUntil { model.state.value.total == 2 }
        return model
    }

    @Test
    fun rowsCarryTheCurrentCodeAndItsExpiry() {
        val state = model().state.value
        assertEquals(listOf("GitHub", "AWS"), state.rows.map { it.issuer })
        assertEquals(Totp.code(secret, now).code, state.rows[0].code)
        assertEquals(Totp.code(secret, now, digits = 8).code, state.rows[1].code)
        assertEquals(60_000L, state.rows[0].expiresAtMillis)
    }

    @Test
    fun searchMatchesTheServiceOrTheAccount() {
        val model = model()
        model.search("aws")
        awaitUntil { model.state.value.rows.size == 1 }
        assertEquals(
            "AWS",
            model.state.value.rows
                .single()
                .issuer,
        )
        model.search("brightleaf")
        awaitUntil { model.state.value.rows.size == 2 }
        model.search("razorpay")
        awaitUntil {
            model.state.value.rows
                .isEmpty()
        }
        assertEquals(2, model.state.value.total)
    }

    @Test
    fun deleteAndRename() {
        val model = model()
        val github =
            model.state.value.rows
                .first()
        model.rename(github.id, "GitHub (work)", "ops")
        awaitUntil {
            model.state.value.rows
                .first()
                .issuer == "GitHub (work)"
        }
        model.delete(
            model.state.value.rows
                .last(),
        )
        awaitUntil { model.state.value.total == 1 }
        assertEquals(AccountsMessage.Deleted("AWS"), model.events.awaitFirst())
    }

    @Test
    fun copyingSaysCopied() {
        val model = model()
        model.copy(
            model.state.value.rows
                .first(),
        )
        assertEquals(AccountsMessage.Copied, model.events.awaitFirst())
        assertTrue(
            model.state.value.rows
                .isNotEmpty(),
        )
    }
}
