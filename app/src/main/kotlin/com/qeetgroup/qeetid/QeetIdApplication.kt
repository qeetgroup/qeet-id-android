package com.qeetgroup.qeetid

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.qeetgroup.qeetid.approvals.push.SignInNotifications

open class QeetIdApplication : Application() {
    val container: AppContainer by lazy { createContainer() }

    /** The app's objects; tests run a subclass that builds them with fakes. */
    protected open fun createContainer(): AppContainer = AppContainer.create(this)

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
}

/** The app's [AppContainer], from any context. */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as QeetIdApplication).container
