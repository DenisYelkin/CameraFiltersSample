package com.elkin.sample.ui.fragment

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.elkin.sample.R
import com.elkin.sample.ui.util.setOnSingleClickListener
import com.elkin.sample.util.getRequiredPermissions
import com.elkin.sample.util.isPermissionsGranted
import kotlinx.android.synthetic.main.fragment_permissions.*

/**
 * @author elkin
 */

private const val REQUEST_CODE_PERMISSIONS = 1
private const val REQUEST_CODE_APP_SETTINGS = 2

class PermissionsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_permissions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        permissionsFragment_titleTextView.text =
            getString(R.string.permissionsFragment_title, getString(R.string.app_name))
        setContentVisibility(View.GONE)

        requestPermissions(requireContext().getRequiredPermissions(), REQUEST_CODE_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            var permissionsGranted = true
            for ((index, grantResult) in grantResults.withIndex()) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    permissionsGranted = false
                    if (shouldShowRequestPermissionRationale(permissions[index])) {
                        permissionsFragment_messageTextView.setText(R.string.permissionsFragment_rationaleMessage)
                        permissionsFragment_requestPermissionButton.setText(R.string.permissionsFragment_requestPermissionsButton)
                        permissionsFragment_requestPermissionButton.setOnSingleClickListener {
                            requestPermissions(
                                requireContext().getRequiredPermissions(),
                                REQUEST_CODE_PERMISSIONS
                            )
                        }
                        setContentVisibility(View.VISIBLE)

                    } else {
                        setRequirementsState()
                    }
                }
            }
            if (permissionsGranted) {
                moveToMainState()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_APP_SETTINGS) {
            if (requireContext().isPermissionsGranted()) {
                moveToMainState()

            } else {
                setRequirementsState()
            }
        }
    }

    private fun setRequirementsState() {
        permissionsFragment_messageTextView.text = getString(
            R.string.permissionsFragment_requirementsMessage,
            getString(R.string.app_name)
        )
        permissionsFragment_requestPermissionButton.setText(R.string.permissionsFragment_openSettingsButton)
        permissionsFragment_requestPermissionButton.setOnSingleClickListener {
            val intent = Intent()
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.data = Uri.parse("package:${requireContext().packageName}")
            startActivityForResult(intent, REQUEST_CODE_APP_SETTINGS)
        }
        setContentVisibility(View.VISIBLE)
    }

    private fun moveToMainState() {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, CameraFragment())
            .commit()
    }

    private fun setContentVisibility(visibility: Int) {
        permissionsFragment_titleTextView.visibility = visibility
        permissionsFragment_messageTextView.visibility = visibility
        permissionsFragment_iconImageView.visibility = visibility
        permissionsFragment_requestPermissionButton.visibility = visibility
    }
}