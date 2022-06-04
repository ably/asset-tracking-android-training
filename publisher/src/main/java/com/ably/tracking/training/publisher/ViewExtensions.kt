package com.ably.tracking.training.publisher

import android.view.View
import android.widget.Button

fun View.hide() {
    visibility = View.GONE
}

fun View.show() {
    visibility = View.VISIBLE
}

fun Button.hideText() {
    textScaleX = 0f
}

fun Button.showText() {
    textScaleX = 1f
}
