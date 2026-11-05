package com.qeetgroup.qeetid.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private var now = 0L
    private var forgotten = 0

    private fun lock(
        enabled: Boolean = true,
        secure: Boolean = true,
    ) = AppLock({ enabled }, { secure }, { now }, { forgotten++ })

    @Test
    fun opensLockedWhenOnAndThePhoneHasAScreenLock() {
        assertTrue(lock().locked.value)
        assertFalse(lock(enabled = false).locked.value)
        // No screen lock: nothing to verify with, so the app opens (as the React Native app did).
        assertFalse(lock(secure = false).locked.value)
    }

    @Test
    fun locksAgainAfterThirtySecondsAway() {
        val lock = lock()
        lock.unlock()
        lock.onBackground()
        now += AppLock.GRACE_MILLIS - 1
        lock.onForeground()
        assertFalse("a quick switch to paste a code does not lock", lock.locked.value)
        lock.onBackground()
        now += AppLock.GRACE_MILLIS
        lock.onForeground()
        assertTrue(lock.locked.value)
        assertTrue("locking drops the secrets read for codes", forgotten > 0)
    }

    @Test
    fun turningTheLockOffReleasesIt() {
        val lock = lock()
        lock.release()
        assertFalse(lock.locked.value)
    }
}
