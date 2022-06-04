package com.ably.tracking.training.subscriber

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private var googleMap: GoogleMap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prepareMap()
        clearTrackableStatusInfo()
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
}