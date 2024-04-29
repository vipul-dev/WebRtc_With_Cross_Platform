package com.dev.recordifiedwebrtc

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

internal class PermissionChecker {
    private val REQUEST_MULTIPLE_PERMISSION = 100
    private var callbackMultiple: VerifyPermissionsCallback? = null
    fun verifyPermissions(
        activity: Activity,
        permissions: Array<String>,
        callback: VerifyPermissionsCallback?
    ) {
        val denyPermissions = getDenyPermissions(activity, permissions)
        if (denyPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                denyPermissions,
                REQUEST_MULTIPLE_PERMISSION
            )
            callbackMultiple = callback
        } else {
            callback?.onPermissionAllGranted()
        }
    }

    private fun getDenyPermissions(context: Context, permissions: Array<String>): Array<String> {
        val denyPermissions = ArrayList<String>()
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                denyPermissions.add(permission)
            }
        }
        return denyPermissions.toTypedArray<String>()
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_MULTIPLE_PERMISSION -> if (grantResults.isNotEmpty() && callbackMultiple != null) {
                val denyPermissions = ArrayList<String>()
                for ((i, permission) in permissions.withIndex()) {
                    if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        denyPermissions.add(permission)
                    }
                }
                if (denyPermissions.size == 0) {
                    callbackMultiple!!.onPermissionAllGranted()
                } else {
                    callbackMultiple!!.onPermissionDeny(denyPermissions.toTypedArray<String>())
                }
            }
        }
    }

    interface VerifyPermissionsCallback {
        fun onPermissionAllGranted()
        fun onPermissionDeny(permissions: Array<String>?)
    }

    companion object {
        fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
            return true
        }
    }
}
