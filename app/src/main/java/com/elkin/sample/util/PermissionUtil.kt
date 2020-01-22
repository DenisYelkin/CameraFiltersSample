package com.elkin.sample.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * @author elkin
 */
fun Context.getRequiredPermissions() = mutableListOf<String>().apply {
    if (!isPermissionGranted(Manifest.permission.CAMERA)) {
        add(Manifest.permission.CAMERA)
    }
    if (!isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}.toTypedArray()

fun Context.isPermissionsGranted() = getRequiredPermissions().isEmpty()

fun Context.isPermissionGranted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED