package com.ably.tracking.training.publisher

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupInitialViews()
    }

    private fun setupInitialViews() {
        trackableIdTextView.text = getString(R.string.trackable_id_info, "")
        trackableStatusTextView.text = getString(R.string.trackable_status_info, "")
        trackableLatitudeTextView.text = getString(R.string.trackable_latitude_info, "")
        trackableLongitudeTextView.text = getString(R.string.trackable_longitude_info, "")
    }
}