package com.ably.tracking.training.publisher

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.ably.tracking.ConnectionException
import com.ably.tracking.TrackableState
import com.ably.tracking.publisher.Trackable
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.random.Random

class MainActivity : PublisherServiceActivity() {
    private val TAG = this::class.simpleName

    // SupervisorJob() is used to keep the scope working after any of its children fail
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var trackable: Trackable? = null
    private var trackableStateFlow: StateFlow<TrackableState>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupInitialState()
        setupButtonListeners()
        if (!PermissionsHelper.hasFineOrCoarseLocationPermissionGranted(this)) {
            PermissionsHelper.requestLocationPermission(this)
        }
    }

    override fun onPublisherServiceConnected(publisherService: PublisherService) {
        if (!publisherService.isPublisherStarted) {
            startPublisher()
        } else {
            switchToPublisherStartedState()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionsHelper.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults,
            onLocationPermissionGranted = { Toast.makeText(this, "Permissions granted", Toast.LENGTH_LONG).show() }
        )
    }

    private fun startPublisherClicked() {
        if (!PermissionsHelper.hasFineOrCoarseLocationPermissionGranted(this)) {
            PermissionsHelper.requestLocationPermission(this)
            return
        }
        showStartPublisherButtonLoading()
        scope.launch {
            if (isPublisherServiceStarted()) {
                startPublisher()
            } else {
                startAndBindPublisherService()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPublisher() {
        try {
            publisherService?.startPublisher()
            hideStartPublisherButtonLoading()
            switchToPublisherStartedState()
            publisherService?.publisher?.locations
                ?.onEach { updateTrackableLocationInfo(it.location.latitude, it.location.longitude) }
                ?.launchIn(scope)
        } catch (connectionException: ConnectionException) {
            showToast("Failed to start the publisher")
        }
    }

    private fun addTrackableClicked() {
        showAddTrackableButtonLoading()
        scope.launch {
            var isOperationSuccessful = false
            val trackableId = generateRandomTrackableId()
            publisherService?.publisher?.let { publisher ->
                try {
                    trackable = Trackable(trackableId).also {
                        trackableStateFlow = publisher.add(it)
                    }
                    isOperationSuccessful = true
                } catch (exception: ConnectionException) {
                    showToast("Failed to add a trackable")
                    Log.d(TAG, exception.message, exception)
                }
            }
            hideAddTrackableButtonLoading()
            if (isOperationSuccessful) {
                updateTrackableIdInfo(trackableId)
                switchToTrackableAddedState()
                trackableStateFlow
                    ?.onEach { updateTrackableStateInfo(it) }
                    ?.launchIn(scope)
            }
        }
    }

    private fun removeTrackableClicked() {
        showRemoveTrackableButtonLoading()
        scope.launch {
            var isOperationSuccessful = false
            publisherService?.publisher?.let { publisher ->
                trackable?.let {
                    try {
                        publisher.remove(it)
                        isOperationSuccessful = true
                    } catch (exception: ConnectionException) {
                        showToast("Failed to remove the trackable")
                        Log.d(TAG, exception.message, exception)
                    }
                }
            }
            hideRemoveTrackableButtonLoading()
            if (isOperationSuccessful) {
                clearTrackableInfo()
                switchToTrackableRemovedState()
                trackable = null
                trackableStateFlow = null
            }
        }
    }

    private fun stopPublisherClicked() {
        showStopPublisherButtonLoading()
        scope.launch {
            try {
                publisherService?.stopPublisher()
                stopPublisherService()
                hideStopPublisherButtonLoading()
                clearTrackableInfo()
                switchToPublisherStoppedState()
            } catch (exception: ConnectionException) {
                showToast("Failed to stop the publisher")
                hideStopPublisherButtonLoading()
            } catch (timeoutException: TimeoutCancellationException) {
                showToast("Timeout when stopping the publisher")
                hideStopPublisherButtonLoading()
            }
        }
    }

    private fun setupInitialState() {
        clearTrackableInfo()
    }

    private fun setupButtonListeners() {
        startPublisherButton.setOnClickListener { startPublisherClicked() }
        addTrackableButton.setOnClickListener { addTrackableClicked() }
        removeTrackableButton.setOnClickListener { removeTrackableClicked() }
        stopPublisherButton.setOnClickListener { stopPublisherClicked() }
    }

    private fun switchToPublisherStartedState() {
        startPublisherButton.isEnabled = false
        addTrackableButton.isEnabled = true
        removeTrackableButton.isEnabled = false
        stopPublisherButton.isEnabled = true
    }

    private fun switchToPublisherStoppedState() {
        startPublisherButton.isEnabled = true
        addTrackableButton.isEnabled = false
        removeTrackableButton.isEnabled = false
        stopPublisherButton.isEnabled = false
    }

    private fun switchToTrackableAddedState() {
        addTrackableButton.isEnabled = false
        removeTrackableButton.isEnabled = true
    }

    private fun switchToTrackableRemovedState() {
        addTrackableButton.isEnabled = true
        removeTrackableButton.isEnabled = false
    }

    private fun showStartPublisherButtonLoading() {
        startPublisherButton.isEnabled = false
        startPublisherButton.hideText()
        startPublisherProgressBar.show()
    }

    private fun hideStartPublisherButtonLoading() {
        startPublisherButton.isEnabled = true
        startPublisherButton.showText()
        startPublisherProgressBar.hide()
    }

    private fun showAddTrackableButtonLoading() {
        addTrackableButton.isEnabled = false
        addTrackableButton.hideText()
        addTrackableProgressBar.show()
        stopPublisherButton.isEnabled = false
    }

    private fun hideAddTrackableButtonLoading() {
        addTrackableButton.isEnabled = true
        addTrackableButton.showText()
        addTrackableProgressBar.hide()
        stopPublisherButton.isEnabled = true
    }

    private fun showRemoveTrackableButtonLoading() {
        removeTrackableButton.isEnabled = false
        removeTrackableButton.hideText()
        removeTrackableProgressBar.show()
        stopPublisherButton.isEnabled = false
    }

    private fun hideRemoveTrackableButtonLoading() {
        removeTrackableButton.isEnabled = true
        removeTrackableButton.showText()
        removeTrackableProgressBar.hide()
        stopPublisherButton.isEnabled = true
    }

    private fun showStopPublisherButtonLoading() {
        stopPublisherButton.isEnabled = false
        stopPublisherButton.hideText()
        stopPublisherProgressBar.show()
        addTrackableButton.isEnabled = false
        removeTrackableButton.isEnabled = false
    }

    private fun hideStopPublisherButtonLoading() {
        stopPublisherButton.isEnabled = true
        stopPublisherButton.showText()
        stopPublisherProgressBar.hide()
        addTrackableButton.isEnabled = trackable == null
        removeTrackableButton.isEnabled = trackable != null
    }

    private fun updateTrackableIdInfo(trackableId: String) {
        trackableIdTextView.text = getString(R.string.trackable_id_info, trackableId)
    }

    private fun clearTrackableInfo() {
        trackableIdTextView.text = getString(R.string.trackable_id_info, "N/A")
        trackableStatusTextView.text = getString(R.string.trackable_status_info, "N/A")
        trackableLatitudeTextView.text = getString(R.string.trackable_latitude_info, 0.0)
        trackableLongitudeTextView.text = getString(R.string.trackable_longitude_info, 0.0)
    }

    private fun updateTrackableStateInfo(state: TrackableState) {
        trackableStatusTextView.text = getString(R.string.trackable_status_info, state.javaClass.simpleName)
    }

    private fun updateTrackableLocationInfo(latitude: Double, longitude: Double) {
        trackableLatitudeTextView.text = getString(R.string.trackable_latitude_info, latitude)
        trackableLongitudeTextView.text = getString(R.string.trackable_longitude_info, longitude)
    }

    /**
     * Generate random numeric ID of length 4 (e.g. "4365")
     */
    private fun generateRandomTrackableId(): String =
        Random(System.currentTimeMillis()).nextInt(from = 1_000, until = 10_000).toString()

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}