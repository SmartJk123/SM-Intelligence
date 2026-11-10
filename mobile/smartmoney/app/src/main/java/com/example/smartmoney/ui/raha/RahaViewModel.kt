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

    private var lastBriefedRoute: String? = null
    private var currentPageData: Map<String, String> = emptyMap()
    private var nudgeJob: kotlinx.coroutines.Job? = null

    fun openSheet() {
        dismissPromptNudge()
        _uiState.update { it.copy(isOpen = true, hasUnreadAlert = false, isPromptNudgeVisible = false) }
    }

    fun openForScreen(
        route: String,
        title: String,
        pageData: Map<String, String> = emptyMap()
    ) {
        dismissPromptNudge()
        val normalizedRoute = route.lowercase().trim()
        val shouldFetchBriefing = (lastBriefedRoute != normalizedRoute) || _uiState.value.messages.isEmpty()

        currentPageData = pageData
        lastBriefedRoute = normalizedRoute

        _uiState.update {
            it.copy(
                isOpen = true,
                hasUnreadAlert = false,
                isPromptNudgeVisible = false,
                currentScreenRoute = normalizedRoute,
                currentScreenTitle = title
            )
        }

        if (shouldFetchBriefing) {
            fetchPageBriefing(normalizedRoute, title, pageData)
        }
    }

    fun triggerPromptNudgeForScreen(route: String) {
        if (_uiState.value.isOpen) return

        nudgeJob?.cancel()
        nudgeJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1200L)
            if (_uiState.value.isOpen) return@launch

            val nudge = rahaRepository.getPromptNudge(route)
            _uiState.update {
                it.copy(
                    promptNudge = nudge,
                    isPromptNudgeVisible = true
                )
            }

            kotlinx.coroutines.delay(8000L)
            _uiState.update { it.copy(isPromptNudgeVisible = false) }
        }
    }

    fun dismissPromptNudge() {
        nudgeJob?.cancel()
        _uiState.update { it.copy(isPromptNudgeVisible = false) }
    }

    fun refreshScreenBriefing() {
        val route = _uiState.value.currentScreenRoute ?: "dashboard"
        val title = _uiState.value.currentScreenTitle ?: "Overview"
        fetchPageBriefing(route, title, currentPageData)
    }

    private fun fetchPageBriefing(
        route: String,
        title: String,
        pageData: Map<String, String>
    ) {
        _uiState.update { it.copy(isTyping = true) }

        viewModelScope.launch {
            val result = rahaRepository.getPageBriefing(
                screenRoute = route,
                screenTitle = title,
                conversationId = _uiState.value.conversationId,
                pageData = pageData
            )

            result.fold(
                onSuccess = { briefing ->
                    _uiState.update { current ->
                        current.copy(
                            messages = current.messages + briefing,
                            conversationId = briefing.conversationId ?: current.conversationId,
                            isTyping = false,
                            quickReplies = if (briefing.quickReplies.isNotEmpty()) {
                                briefing.quickReplies
                            } else {
                                current.quickReplies
                            }
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isTyping = false) }
                }
            )
        }
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

        val currentRoute = _uiState.value.currentScreenRoute
        val currentTitle = _uiState.value.currentScreenTitle

        _uiState.update { current ->
            current.copy(
                messages = current.messages + userMessage,
                isTyping = true
            )
        }

        viewModelScope.launch {
            val result = rahaRepository.sendMessage(
                message = text.trim(),
                conversationId = _uiState.value.conversationId,
                screenRoute = currentRoute,
                screenTitle = currentTitle,
                pageData = currentPageData
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
                onFailure = {
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
        lastBriefedRoute = null
        val currentRoute = _uiState.value.currentScreenRoute ?: "dashboard"
        val currentTitle = _uiState.value.currentScreenTitle ?: "Overview"

        _uiState.update {
            it.copy(
                messages = emptyList(),
                isTyping = false,
                quickReplies = initialGreeting.quickReplies,
                conversationId = null
            )
        }

        fetchPageBriefing(currentRoute, currentTitle, currentPageData)
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
