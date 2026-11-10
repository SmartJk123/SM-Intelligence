package com.smi.assistant_service.llm;

import com.smi.assistant_service.dto.RahaChatResponse;
import com.smi.assistant_service.model.FinancialContext;

public interface LlmClient {

    RahaChatResponse processChat(String userMessage, String conversationId, FinancialContext context);

    boolean isConfigured();

    default RahaChatResponse generatePageBriefing(String conversationId, FinancialContext context) {
        String screenTitle = context.getScreenContext() != null && context.getScreenContext().getTitle() != null
                ? context.getScreenContext().getTitle()
                : (context.getScreenContext() != null ? context.getScreenContext().getRoute() : "Overview");
        return processChat(
                "Please provide a helpful, engaging briefing and highlights for the " + screenTitle + " screen based on its contents.",
                conversationId,
                context
        );
    }
}
