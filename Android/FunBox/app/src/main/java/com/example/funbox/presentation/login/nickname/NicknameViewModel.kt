package com.example.funbox.presentation.login.nickname

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.funbox.data.repository.NaverLoginRepository
import com.example.funbox.data.repository.NaverLoginRepositoryImpl
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NicknameViewModel : ViewModel() {

    private val naverLoginRepository: NaverLoginRepository = NaverLoginRepositoryImpl()

    private val userNickname = MutableStateFlow<String>("")

    private val _nicknameUiEvent = MutableSharedFlow<NicknameUiEvent>()
    val nicknameUiEvent = _nicknameUiEvent.asSharedFlow()

    private val _nicknameUiState = MutableStateFlow<NicknameUiState>(NicknameUiState())
    val nicknameUiState = _nicknameUiState.asStateFlow()

    fun validateUserNickname(id: CharSequence) {
        if (id.isBlank()) {
            _nicknameUiState.update { uiState ->
                uiState.copy(nicknameValidState = NicknameValidState.None)
            }
        }
        else {
            _nicknameUiState.update { uiState ->
                uiState.copy(nicknameValidState = NicknameValidState.Valid)
            }
        }
        userNickname.value = id.toString()
    }

    fun submitNickname() {
        viewModelScope.launch {

        }
    }

    fun goToProfileFragment() {
        viewModelScope.launch {
            _nicknameUiEvent.emit(NicknameUiEvent.NicknameSuccess)
        }
    }
}