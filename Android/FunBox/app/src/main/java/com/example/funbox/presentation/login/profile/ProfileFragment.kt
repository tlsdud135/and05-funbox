package com.example.funbox.presentation.login.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import com.example.funbox.R
import com.example.funbox.databinding.FragmentProfileBinding
import com.example.funbox.presentation.BaseFragment
import com.example.funbox.presentation.MainActivity
import com.example.funbox.presentation.login.AccessPermission

class ProfileFragment : BaseFragment<FragmentProfileBinding>(R.layout.fragment_profile) {

    private val viewModel: ProfileViewModel by viewModels()
    private val profileImagePicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            this.viewModel.selectProfile(uri)
        }
    private val requestMultiPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            var flag = true
            it.entries.forEach { entry ->
                flag = entry.value
            }
            if (flag) {
                profileImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.vm = viewModel

        collectLatestFlow(viewModel.profileUiEvent) { handleUiEvent(it) }
    }

    private fun handleUiEvent(event: ProfileUiEvent) = when (event) {
        is ProfileUiEvent.ProfileSelect -> {
            requestMultiPermissions.launch(AccessPermission.profilePermissionList)
        }

        is ProfileUiEvent.ProfileSuccess -> {
            startActivity(Intent(requireActivity(), MainActivity::class.java))
        }

        is ProfileUiEvent.NetworkErrorEvent -> {
            showSnackBar(R.string.network_error_message)
        }
    }
}