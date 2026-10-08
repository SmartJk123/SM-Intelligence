package com.example.smartmoney.ui.raha

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartmoney.domain.model.RahaMessage
import com.example.smartmoney.domain.model.RahaSender
import com.example.smartmoney.domain.repository.RahaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RahaViewModel(
    private val rahaRepository: RahaRepository
) : ViewModel() {

    private val initialGreeting = RahaMessage(
        sender = RahaSender.RAHA,
        text = "Habari! I am Raha, your personal financial assistant. How can I help you manage your money today?",
        quickReplies = listOf(
            "What is my total balance?",
            "Check my Budgets",
            "Recent Transactions",
            "Add an Invoice"
        )
    )

    private val _uiState = MutableStateFlow(
        RahaUiState(
            messages = listOf(initialGreeting)
        )
    )
    val uiState: StateFlow<RahaUiState> = _uiState.asStateFlow()

    fun openSheet() {
        _uiState.update { it.copy(isOpen = true, hasUnreadAlert = false) }
    }

    fun closeSheet() {
        _uiState.update { it.copy(isOpen = false) }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = RahaMessage(
            sender = RahaSender.USER,
            text = text.trim()
        )

        _uiState.update { current ->
            current.copy(
                messages = current.messages + userMessage,
                isTyping = true
            )
        }

        viewModelScope.launch {
            val result = rahaRepository.sendMessage(
                message = text.trim(),
                conversationId = _uiState.value.conversationId
            )

            result.fold(
                onSuccess = { reply ->
                    _uiState.update { current ->
                        current.copy(
                            messages = current.messages + reply,
                            conversationId = reply.conversationId ?: current.conversationId,
                            isTyping = false,
                            quickReplies = if (reply.quickReplies.isNotEmpty()) {
                                reply.quickReplies
                            } else {
                                current.quickReplies
                            }
                        )
                    }
                },
                onFailure = { error ->
                    val errorMessage = RahaMessage(
                        sender = RahaSender.RAHA,
                        text = "I encountered an issue retrieving that. Please try again in a moment.",
                        quickReplies = listOf("What is my total balance?", "Check my Budgets")
                    )
                    _uiState.update { current ->
                        current.copy(
                            messages = current.messages + errorMessage,
                            isTyping = false
                        )
                    }
                }
            )
        }
    }

    fun clearConversation() {
        val currentConvId = _uiState.value.conversationId
        if (currentConvId != null) {
            viewModelScope.launch {
                rahaRepository.clearConversation(currentConvId)
            }
        }
        _uiState.update {
            it.copy(
                messages = listOf(initialGreeting),
                isTyping = false,
                quickReplies = initialGreeting.quickReplies,
                conversationId = null
            )
        }
    }

    class Factory(
        private val repository: RahaRepository
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(RahaViewModel::class.java)) {
                return RahaViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
