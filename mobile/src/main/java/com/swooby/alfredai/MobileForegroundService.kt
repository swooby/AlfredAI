package com.swooby.alfredai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.swooby.alfredai.AppUtils.showToast
import com.swooby.alfredai.MobileViewModel.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MobileForegroundService : Service() {
    companion object {
        private const val TAG = "MobileForegroundService"

        /**
         * Must compliment AndroidManifest's `foregroundServiceType` value for this service.
         */
        const val FOREGROUND_SERVICE_TYPE =
            //ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            //ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

        private const val ACTION_DISCONNECT = "com.swooby.alfredai.action.DISCONNECT"

        private const val CHANNEL_ID = "FOREGROUND_SERVICE_CHANNEL"
        private const val CHANNEL_NAME = "Foreground Service Channel"
        private const val CHANNEL_DESCRIPTION = "Non-dismissible notifications for session status"
        private const val NOTIFICATION_ID_SERVICE = 101

        private const val CONNECTION_NAME = "OpenAI Realtime"

        fun start(context: Context) {
            val intent = Intent(context, MobileForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MobileForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private val mobileViewModel by lazy { (application as AlfredAiApp).mobileViewModel }

    private var isForegroundStarted = false

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        Log.d(TAG, "onCreate()")
        super.onCreate()
        createNotificationChannel()
        observeConnectionStateForNotifications()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        super.onDestroy()
        isForegroundStarted = false
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand(intent=$intent)")
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                Log.i(TAG, "onStartCommand: ACTION_DISCONNECT")
                disconnectAndStopForegroundService()
            }
        }
        return START_STICKY
    }

    private fun observeConnectionStateForNotifications() {
        serviceScope.launch {
            mobileViewModel.connectionStateFlow.collect { connectionState ->
                Log.i(TAG, "connectionStateFlow: connectionState=$connectionState")
                showNotification(connectionState)
            }
        }
    }

    private fun showNotification(connectionState: ConnectionState) {
        Log.d(TAG, "showNotification(connectionState=$connectionState)")
        when (connectionState) {
            ConnectionState.Connecting -> {
                try {
                    isForegroundStarted = true
                    startForeground(
                        NOTIFICATION_ID_SERVICE,
                        createNotification(
                            contentTitle = "AlfredAI: Connecting...",
                            contentText = "Connecting to $CONNECTION_NAME..."
                        ),
                        FOREGROUND_SERVICE_TYPE,
                    )
                } catch (e: SecurityException) {
                    Log.e(TAG, "connectionStateFlow: SecurityException: ${e.message}")
                    isForegroundStarted = false
                    /*

                    I see this if I press the `Home` button immediately after launching the app...
                    ...IF WE ARE USING THE PREFERRED [in AndroidManifest.xml]:
                    * `<service ... foregroundServiceType="microphone" ... >`
                    * `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />`

                    `MobileForegroundService com.swooby.alfredai E onStartCommand: SecurityException:
                     Starting FGS with type microphone callerApp=ProcessRecord{28abe4 12711:com.swooby.alfredai/u0a211}
                     targetSDK=34 requires permissions: all of the permissions allOf=true [android.permission.FOREGROUND_SERVICE_MICROPHONE]
                     any of the permissions allOf=false [android.permission.CAPTURE_AUDIO_HOTWORD, android.permission.CAPTURE_AUDIO_OUTPUT,
                     android.permission.CAPTURE_MEDIA_OUTPUT, android.permission.CAPTURE_TUNER_AUDIO_INPUT,
                     android.permission.CAPTURE_VOICE_COMMUNICATION_OUTPUT, android.permission.RECORD_AUDIO]
                     and the app must be in the eligible state/exemptions to access the foreground only permission`

                    The key phrase is that last line.

                    https://developer.android.com/develop/background-work/services/fgs/service-types#microphone

                    "Note: The RECORD_AUDIO runtime permission is subject to while-in-use restrictions.
                     For this reason, you cannot create a microphone foreground service while your app
                      is in the background ..."

                    https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#wiu-restrictions

                    "For while-in-use permissions, this causes a potential problem. If your app has
                     a while-in-use permission, it only has that permission while it's in the foreground.
                     This means if your app is in the background, and it tries to create a foreground
                     service of type camera, location, or microphone, the system sees that your app
                     doesn't currently have the required permissions, and it throws a SecurityException."
                    "For this reason, if your foreground service needs a while-in-use permission, you
                     must call Context.startForegroundService() or Context.bindService() while your
                     app has a visible activity, unless the service falls into one of the `defined exemptions`."

                    https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#wiu-restrictions-exemptions

                    None of the exemptions realistically apply to this app.

                    They naively think that it is acceptable to only start a service when the Activity
                    is visible.
                    Sure, we could fairly simply write code to wait until the Activity UI is visible
                    and **then** start the service, but it is still easy to hit `Home` at the right/wrong
                    moment before `startForeground` is called and still cause this code to fail.

                    I could see creating a `MutableStateFlow` that is observed for changes when an Activity
                    is added, and only then trying to immediately call `startForeground`, but even that
                    still screams of `race-condition` problems.

                    They introduced a race-condition back in API26 (2017), and have continued to kick
                    the can down the road on that race-condition, making it worse by piling on even
                    more pointless restrictions rather than fix the problem:
                    https://stackoverflow.com/a/49418249/252308 (2017/06)
                    https://issuetracker.google.com/issues/76112072 (2018/05)

                    Instead of fighting all of this, I have chosen to implement the foreground service as:
                    * `<service ... foregroundServiceType="specialUse" ... >`
                    * `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />`
                    This works  perfectly fine for non-PlayStore apps.
                    If this app ever gets on the PlayStore then they can review it and accept or reject
                    its specialUse case then.
                    If they reject then I could always try the observable `MutableStateFlow` option.

                    For more info, see:
                    https://developer.android.com/develop/background-work/services/fgs/launch

                    */
                    showToast(this@MobileForegroundService, "Don't press `Home` while the app is starting!", Toast.LENGTH_LONG)
                    disconnectAndStopForegroundService()
                }
            }
            ConnectionState.Connected -> {
                updateNotification(
                    contentTitle = "AlfredAI: Connected",
                    contentText = "Connected to $CONNECTION_NAME"
                )
            }
            ConnectionState.Disconnected -> {
                disconnectAndStopForegroundService()
            }
        }
    }

    private fun disconnectAndStopForegroundService() {
        Log.d(TAG, "disconnectAndStopForegroundService()")
        if (isForegroundStarted) {
            isForegroundStarted = false
            Log.d(TAG, "disconnectAndStopForegroundService: disconnecting")
            mobileViewModel.disconnect()
            Log.d(TAG, "disconnectAndStopForegroundService: showing disconnected notification")
            updateNotification(
                contentTitle = "AlfredAI: Disconnected",
                contentText = "Disconnected from $CONNECTION_NAME"
            )
            Log.d(TAG, "disconnectAndStopForegroundService: stopping foreground service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

    private fun createNotificationChannel() {
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
            description = CHANNEL_DESCRIPTION
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(contentTitle: String, contentText: String): Notification {
        val context = this

        val notificationPendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MobileActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectPendingIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, MobileForegroundService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.alfredai_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(notificationPendingIntent)
        if (isForegroundStarted) {
            val person = Person.Builder()
                .setName(CONNECTION_NAME)
                .setIcon(IconCompat.createWithResource(context, R.drawable.alfredai_24))
                .setImportant(true)
                .setBot(true)
                .build()
            builder
                .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, disconnectPendingIntent))
        }
        return builder.build()
    }

    private fun updateNotification(contentTitle: String, contentText: String) {
        val notification = createNotification(contentTitle, contentText)
        notificationManager.notify(NOTIFICATION_ID_SERVICE, notification)
    }
}
