package com.qeetgroup.qeetid.approvals.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.qeetgroup.qeetid.appContainer

/**
 * Firebase Cloud Messaging: sign-in requests arrive as high-priority data messages, and new push tokens are sent to
 * the server with the phone's device credential. Firebase starts this service only in a build with a Firebase config.
 */
class QeetIdMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        PushMessages.handle(applicationContext, message.data)
    }

    override fun onNewToken(token: String) {
        applicationContext.appContainer.pushSync.onNewToken(token)
    }
}
