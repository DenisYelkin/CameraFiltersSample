package com.elkin.sample.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.elkin.sample.R
import com.elkin.sample.ui.MainViewModel
import com.elkin.sample.ui.util.setOnSigleClickListener
import kotlinx.android.synthetic.main.fragment_error.*

/**
 *
 * @author Elkin
 * @version $Id$
 */
class ErrorFragment : Fragment() {

    private val viewModel by lazy {
        ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_error, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        errorFragment_descriptionTextView.text = viewModel.errorMessage
        errorFragment_actionButton
            .setText(if (viewModel.tryAgainAvailable) R.string.errorFragment_backButton else R.string.errorFragment_exitButton)
        errorFragment_actionButton.setOnSigleClickListener {
            if (viewModel.tryAgainAvailable) {
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, CameraFragment())
                    .commit()

            } else {
                requireActivity().finish()
            }
        }
    }
}