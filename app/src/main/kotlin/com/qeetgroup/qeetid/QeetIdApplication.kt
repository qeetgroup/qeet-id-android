package com.qeetgroup.qeetid

import android.app.Application
import com.qeetgroup.qeetid.approvals.push.SignInNotifications
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class QeetIdApplication : Application() {
    /** Made on first use, so tests that replace it never build the production one. */
    val container: AppContainer by lazy { containerOverride ?: AppContainer.create(this) }

    override fun onCreate() {
        super.onCreate()
        SignInNotifications.ensureChannel(this)
        // Firebase may have replaced the push token while the app was not running.
        container.pushSync.syncCurrent()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    container.lock.onForeground()
                    // A request whose push was lost, or that no push could carry, is opened when the app comes back.
                    container.inbox.checkPending()
                }

                override fun onStop(owner: LifecycleOwner) = container.lock.onBackground()
            },
        )
    }

    companion object {
        /** Set by tests before the application is created. */
        @Volatile
        var containerOverride: AppContainer? = null
    }
}

/** The app's [AppContainer], from any context. */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as QeetIdApplication).container
