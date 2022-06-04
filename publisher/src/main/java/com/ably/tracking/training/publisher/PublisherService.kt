package com.ably.tracking.training.publisher

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.ably.tracking.Accuracy
import com.ably.tracking.Resolution
import com.ably.tracking.connection.Authentication
import com.ably.tracking.connection.ConnectionConfiguration
import com.ably.tracking.publisher.DefaultResolutionPolicyFactory
import com.ably.tracking.publisher.MapConfiguration
import com.ably.tracking.publisher.Publisher
import com.ably.tracking.publisher.PublisherNotificationProvider
import kotlinx.coroutines.*

// The public token for the Mapbox SDK. For more details see the README.
private const val MAPBOX_ACCESS_TOKEN = BuildConfig.MAPBOX_ACCESS_TOKEN

// The API KEY for the Ably SDK. For more details see the README.
private const val ABLY_API_KEY = BuildConfig.ABLY_API_KEY

// The client ID for the Ably SDK instance.
private const val CLIENT_ID = "TrainingPublisherApp"

class PublisherService : Service() {
    private val TAG = this::class.simpleName

    // SupervisorJob() is used to keep the scope working after any of its children fail
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val NOTIFICATION_ID = 5235
    private lateinit var notification: Notification
    private val binder = Binder()
    var publisher: Publisher? = null

    val isPublisherStarted: Boolean
        get() = publisher != null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Training Publisher")
            .setContentText("Publisher is working")
            .setSmallIcon(R.drawable.aat_logo)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    inner class Binder : android.os.Binder() {
        fun getService(): PublisherService = this@PublisherService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        // We want to be sure that after the service is stopped the publisher is stopped too.
        // Otherwise we could end up with multiple active publishers.
        scope.launch {
            publisher?.stop()
        }
        super.onDestroy()
    }

    /**
     * Creates and starts the [Publisher].
     * This method does nothing if the publisher is already started or if the trackable ID is null or empty.
     */
    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    fun startPublisher() {
        if (!isPublisherStarted) {
            publisher = Publisher.publishers()
                .connection(ConnectionConfiguration(Authentication.basic(CLIENT_ID, ABLY_API_KEY)))
                .map(MapConfiguration(MAPBOX_ACCESS_TOKEN))
                .resolutionPolicy(DefaultResolutionPolicyFactory(Resolution(Accuracy.MAXIMUM, 1000L, 1.0), this))
                .androidContext(this)
                .backgroundTrackingNotificationProvider(
                    object : PublisherNotificationProvider {
                        override fun getNotification(): Notification = notification
                    },
                    NOTIFICATION_ID
                )
                .start()
        }
    }

    suspend fun stopPublisher() {
        if (isPublisherStarted) {
            publisher?.stop()
        }
    }
}
