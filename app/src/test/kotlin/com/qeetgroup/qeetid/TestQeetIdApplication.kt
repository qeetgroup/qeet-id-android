package com.qeetgroup.qeetid

/** The application under Robolectric: the app's objects with a software box and fakes, never the Android Keystore. */
class TestQeetIdApplication : QeetIdApplication() {
    override fun createContainer(): AppContainer = TestContainers.create(this)
}
