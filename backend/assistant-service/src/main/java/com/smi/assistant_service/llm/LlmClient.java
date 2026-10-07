package com.smi.assistant_service.llm;

import com.smi.assistant_service.dto.RahaChatResponse;
import com.smi.assistant_service.model.FinancialContext;

public interface LlmClient {

    RahaChatResponse processChat(String userMessage, String conversationId, FinancialContext context);

    boolean isConfigured();
}
