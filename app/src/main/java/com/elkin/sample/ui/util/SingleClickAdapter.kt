package com.elkin.sample.ui.util

import android.os.SystemClock
import android.view.View

/**
 * @author elkin
 */
abstract class SingleClickAdapter : View.OnClickListener {

    private val oneClickThreshold = 500L

    @Volatile
    private var lastClickTime = -oneClickThreshold

    private fun canPerform() = canPerform(lastClickTime).also { canBeClicked ->
        if (canBeClicked) {
            lastClickTime = SystemClock.elapsedRealtime()
        }
    }

    private fun canPerform(lastClickTime: Long) =
        (SystemClock.elapsedRealtime() - lastClickTime) >= oneClickThreshold

    override fun onClick(view: View) {
        if (canPerform()) onOneClick(view)
    }

    abstract fun onOneClick(view: View)
}

fun View.setOnSingleClickListener(onClick: (view: View) -> Unit) {
    this.setOnClickListener(object : SingleClickAdapter() {
        override fun onOneClick(view: View) {
            onClick(view)
        }
    })
}