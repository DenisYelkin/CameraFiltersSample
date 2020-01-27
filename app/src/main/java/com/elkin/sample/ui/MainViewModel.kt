package com.elkin.sample.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 *
 * @author Elkin
 * @version $Id$
 */
class MainViewModel : ViewModel() {
    val state = MutableLiveData<State>()
    var errorMessage = ""
    var tryAgainAvailable = false

    fun setErrorState(errorMessage: String, tryAgainAvailable: Boolean) {
        this.errorMessage = errorMessage
        this.tryAgainAvailable = tryAgainAvailable
        state.postValue(State.ERROR)
    }
}

enum class State {
    CAMERA_PREPARING,
    CAMERA_READY,
    CAPTURE_STARTED,
    CAPTURE_FINISHED,
    ERROR
}