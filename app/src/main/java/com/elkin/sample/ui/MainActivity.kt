package com.elkin.sample.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.elkin.sample.ui.fragment.CameraFragment
import com.elkin.sample.ui.fragment.PermissionsFragment
import com.elkin.sample.util.isPermissionsGranted

/**
 * @author elkin
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .add(
                android.R.id.content,
                if (isPermissionsGranted()) CameraFragment() else PermissionsFragment()
            )
            .commit()
    }
}