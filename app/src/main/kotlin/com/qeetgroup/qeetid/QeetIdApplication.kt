package com.qeetgroup.qeetid

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class QeetIdApplication : Application() {
    /** Made on first use, so tests that replace it never build the production one. */
    val container: AppContainer by lazy { containerOverride ?: AppContainer.create(this) }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = container.lock.onForeground()

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
