package com.ably.tracking.training.subscriber

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.ably.tracking.ConnectionException
import com.ably.tracking.connection.Authentication
import com.ably.tracking.connection.ConnectionConfiguration
import com.ably.tracking.logging.LogHandler
import com.ably.tracking.logging.LogLevel
import com.ably.tracking.subscriber.Subscriber
import com.ably.tracking.ui.animation.Position
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// The client ID for the Ably SDK instance.
private const val CLIENT_ID = "TrainingSubscriberApp"

// The API KEY for the Ably SDK. For more details see the README.
private const val ABLY_API_KEY = BuildConfig.ABLY_API_KEY

private const val ZOOM_LEVEL_STREETS = 15F

class MainActivity : AppCompatActivity() {
    private val TAG = this::class.simpleName

    // SupervisorJob() is used to keep the scope working after any of its children fail
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var subscriber: Subscriber? = null
    private var googleMap: GoogleMap? = null
    private var enhancedMarker: Marker? = null
    private var enhancedAccuracyCircle: Circle? = null
    private var rawMarker: Marker? = null
    private var rawAccuracyCircle: Circle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prepareMap()
        clearTrackableStatusInfo()
        setTrackableIdEditTextListener()
        setupTrackableInputAction()

        startSubscriberButton.setOnClickListener {
            if (subscriber == null) {
                startSubscribing()
            } else {
                stopSubscribing()
            }
        }
    }

    private fun clearTrackableStatusInfo() {
        trackableStatusTextView.text = getString(R.string.trackable_status_info, "N/A")
    }

    private fun prepareMap() {
        (mapFragmentContainerView.getFragment() as SupportMapFragment).let {
            it.getMapAsync { map ->
                map.uiSettings.isZoomControlsEnabled = true
                googleMap = map
            }
        }
    }

    private fun setTrackableIdEditTextListener() {
        trackableIdEditText.addTextChangedListener { trackableId ->
            trackableId?.trim()?.let {
                startSubscriberButton.isEnabled = it.isNotEmpty()
            }
        }
    }

    private fun setupTrackableInputAction() {
        trackableIdEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                startSubscribing()
                true
            } else {
                false
            }
        }
    }

    private fun startSubscribing() {
        trackableIdEditText.text.toString().trim().let { trackableId ->
            if (trackableId.isNotEmpty()) {
                hideKeyboard(trackableIdEditText)
                showLoading()
                scope.launch {
                    try {
                        subscriber = Subscriber.subscribers()
                            .connection(ConnectionConfiguration(Authentication.basic(CLIENT_ID, ABLY_API_KEY)))
                            .trackingId(trackableId)
                            .logHandler(object : LogHandler {
                                override fun logMessage(level: LogLevel, message: String, throwable: Throwable?) {
                                    when (level) {
                                        LogLevel.VERBOSE -> Log.v(TAG, message, throwable)
                                        LogLevel.INFO -> Log.i(TAG, message, throwable)
                                        LogLevel.DEBUG -> Log.d(TAG, message, throwable)
                                        LogLevel.WARN -> Log.w(TAG, message, throwable)
                                        LogLevel.ERROR -> Log.e(TAG, message, throwable)
                                    }
                                }
                            })
                            .start()
                        hideLoading()
                        showStartedSubscriberLayout()
                    } catch (exception: ConnectionException) {
                        showToast("Failed to start the subscriber")
                        hideLoading()
                    }
                }
            } else {
                showToast(R.string.error_no_trackable_id)
            }
        }
    }

    private fun stopSubscribing() {
        showLoading()
        scope.launch {
            try {
                subscriber?.stop()
                subscriber = null
                enhancedMarker = null
                enhancedAccuracyCircle = null
                rawMarker = null
                rawAccuracyCircle = null
                showStoppedSubscriberLayout()
                hideLoading()
            } catch (exception: ConnectionException) {
                showToast("Failed to stop the subscriber")
                hideLoading()
            }
        }
    }

    private fun showStartedSubscriberLayout() {
        googleMap?.clear()
        trackableIdEditText.isEnabled = false
        startSubscriberButton.text = getString(R.string.stop_subscribing)
    }

    private fun showStoppedSubscriberLayout() {
        trackableIdEditText.isEnabled = true
        startSubscriberButton.text = getString(R.string.start_subscribing)
    }

    private fun showToast(@StringRes stringResourceId: Int) {
        Toast.makeText(this, stringResourceId, Toast.LENGTH_SHORT).show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showLoading() {
        progressIndicator.visibility = View.VISIBLE
        startSubscriberButton.isEnabled = false
        startSubscriberButton.hideText()
    }

    private fun hideLoading() {
        progressIndicator.visibility = View.GONE
        startSubscriberButton.isEnabled = true
        startSubscriberButton.showText()
    }

    private fun moveCamera(newPosition: Position, zoomLevel: Float? = null) {
        val position = LatLng(newPosition.latitude, newPosition.longitude)
        val cameraPosition = if (zoomLevel == null) {
            CameraUpdateFactory.newLatLng(position)
        } else {
            CameraUpdateFactory.newLatLngZoom(position, zoomLevel)
        }
        googleMap?.animateCamera(cameraPosition)
    }

    private fun showMarkerOnMap(newPosition: Position, isRaw: Boolean) {
        val currentMarker = if (isRaw) rawMarker else enhancedMarker
        val currentAccuracyCircle = if (isRaw) rawAccuracyCircle else enhancedAccuracyCircle
        if (currentMarker == null || currentAccuracyCircle == null) {
            createMarker(newPosition, isRaw)
            if (!isRaw) {
                moveCamera(newPosition, ZOOM_LEVEL_STREETS)
            }
        } else {
            updateMarker(newPosition, currentMarker, currentAccuracyCircle, isRaw)
        }
    }

    private fun createMarker(newPosition: Position, isRaw: Boolean) {
        googleMap?.apply {
            val position = LatLng(newPosition.latitude, newPosition.longitude)
            val marker = addMarker(
                MarkerOptions()
                    .position(position)
                    .icon(getMarkerIcon(newPosition.bearing, isRaw))
                    .alpha(if (isRaw) 0.5f else 1f)
            )
            val accuracyCircle = addCircle(
                CircleOptions()
                    .center(position)
                    .radius(newPosition.accuracy.toDouble())
                    .strokeColor(if (isRaw) COLOR_RED else COLOR_ORANGE)
                    .fillColor(if (isRaw) COLOR_RED_TRANSPARENT else COLOR_ORANGE_TRANSPARENT)
                    .strokeWidth(2f)
            )
            if (isRaw) {
                rawMarker = marker
                rawAccuracyCircle = accuracyCircle
            } else {
                enhancedMarker = marker
                enhancedAccuracyCircle = accuracyCircle
            }
        }
    }

    private fun updateMarker(newPosition: Position, marker: Marker, accuracyCircle: Circle, isRaw: Boolean) {
        val position = LatLng(newPosition.latitude, newPosition.longitude)
        marker.setIcon(getMarkerIcon(newPosition.bearing, isRaw))
        marker.position = position
        accuracyCircle.center = position
        accuracyCircle.radius = newPosition.accuracy.toDouble()
    }

    private fun getMarkerIcon(bearing: Float, isRaw: Boolean) =
        if (isRaw) BitmapDescriptorFactory.fromResource(getMarkerResourceIdByBearing(bearing, true))
        else BitmapDescriptorFactory.fromResource(getMarkerResourceIdByBearing(bearing, false))

    private fun getMarkerResourceIdByBearing(bearing: Float, isRaw: Boolean): Int {
        return when (bearing.roundToInt()) {
            in 23..67 -> if (isRaw) R.drawable.driver_raw_ne else R.drawable.driver_ne
            in 67..113 -> if (isRaw) R.drawable.driver_raw_e else R.drawable.driver_e
            in 113..158 -> if (isRaw) R.drawable.driver_raw_se else R.drawable.driver_se
            in 158..203 -> if (isRaw) R.drawable.driver_raw_s else R.drawable.driver_s
            in 203..247 -> if (isRaw) R.drawable.driver_raw_sw else R.drawable.driver_sw
            in 247..292 -> if (isRaw) R.drawable.driver_raw_w else R.drawable.driver_w
            in 292..337 -> if (isRaw) R.drawable.driver_raw_nw else R.drawable.driver_nw
            else -> if (isRaw) R.drawable.driver_raw_n else R.drawable.driver_n
        }
    }
}