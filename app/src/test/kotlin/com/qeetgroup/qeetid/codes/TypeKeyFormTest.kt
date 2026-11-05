package com.qeetgroup.qeetid.codes

import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.codes.totp.TotpAlgorithm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeKeyFormTest {
    private fun validate(
        service: String = "Razorpay",
        key: String = "JBSW Y3DP EHPK 3PXP",
    ) = TypeKeyForm.validate(service, " accounts@brightleaffoods.in ", key, 8, 60, TotpAlgorithm.SHA256)

    @Test
    fun aGoodKeyMakesAnAccount() {
        val result = validate()
        assertTrue(result is TypeKeyForm.Result.Valid)
        val account = (result as TypeKeyForm.Result.Valid).account
        assertEquals("Razorpay", account.issuer)
        assertEquals("accounts@brightleaffoods.in", account.label)
        assertEquals(10, account.secret.size)
        assertEquals(8, account.digits)
        assertEquals(60, account.periodSeconds)
        assertEquals(TotpAlgorithm.SHA256, account.algorithm)
    }

    @Test
    fun eachProblemSaysWhatToDo() {
        assertEquals(TypeKeyForm.Result.Invalid(R.string.type_key_service_required, null), validate(service = "  "))
        assertEquals(TypeKeyForm.Result.Invalid(null, R.string.type_key_key_required), validate(key = " "))
        assertEquals(TypeKeyForm.Result.Invalid(null, R.string.type_key_key_invalid), validate(key = "JBSW Y3DP EHPK 3PX1"))
        assertEquals(TypeKeyForm.Result.Invalid(null, R.string.type_key_key_short), validate(key = "JBSWY3DP"))
        assertEquals(
            TypeKeyForm.Result.Invalid(R.string.type_key_service_required, R.string.type_key_key_required),
            validate(service = "", key = ""),
        )
    }
}
