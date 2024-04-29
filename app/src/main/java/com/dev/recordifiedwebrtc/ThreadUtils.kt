package com.dev.recordifiedwebrtc

import android.os.Looper

object ThreadUtils {

    fun checkIsOnMainThread(){
    if (Thread.currentThread() != Looper.getMainLooper().thread){
        throw IllegalStateException("Not on main thread")
    }
    }
}