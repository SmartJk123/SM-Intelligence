package com.smi.assistant_service.service;

import com.smi.assistant_service.dto.RahaChatRequest;
import com.smi.assistant_service.dto.RahaChatResponse;
import com.smi.assistant_service.llm.DeepSeekLlmClient;
import com.smi.assistant_service.llm.GeminiLlmClient;
import com.smi.assistant_service.llm.RuleBasedLlmClient;
import com.smi.assistant_service.model.FinancialContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final FinancialContextAggregator contextAggregator;
    private final ConversationMemoryService memoryService;
    private final GeminiLlmClient geminiClient;
    private final DeepSeekLlmClient deepSeekClient;
    private final RuleBasedLlmClient ruleBasedClient;
    private final String preferredProvider;

    public AssistantService(
            FinancialContextAggregator contextAggregator,
            ConversationMemoryService memoryService,
            GeminiLlmClient geminiClient,
            DeepSeekLlmClient deepSeekClient,
            RuleBasedLlmClient ruleBasedClient,
            @Value("${raha.provider:gemini}") String preferredProvider) {
        this.contextAggregator = contextAggregator;
        this.memoryService = memoryService;
        this.geminiClient = geminiClient;
        this.deepSeekClient = deepSeekClient;
        this.ruleBasedClient = ruleBasedClient;
        this.preferredProvider = preferredProvider != null ? preferredProvider.trim().toLowerCase() : "gemini";
    }

    public RahaChatResponse chat(RahaChatRequest request, String authorization) {
        // 1. Gather sanitized financial context including client-supplied investments
        FinancialContext context = contextAggregator.aggregateContext(authorization, request.getClientInvestments());

        String userMsg = request.getMessage();
        String convId = request.getConversationId();

        RahaChatResponse response = null;

        // 2. Dispatch to configured LLM provider
        if ("deepseek".equals(preferredProvider)) {
            if (deepSeekClient.isConfigured()) {
                response = deepSeekClient.processChat(userMsg, convId, context);
            } else {
                log.info("DeepSeek requested but API key not configured, falling back");
            }
        } else if ("gemini".equals(preferredProvider)) {
            if (geminiClient.isConfigured()) {
                response = geminiClient.processChat(userMsg, convId, context);
            } else {
                log.info("Gemini requested but API key not configured, falling back");
            }
        } else if ("auto".equals(preferredProvider)) {
            if (geminiClient.isConfigured()) {
                response = geminiClient.processChat(userMsg, convId, context);
            }
            if (response == null && deepSeekClient.isConfigured()) {
                response = deepSeekClient.processChat(userMsg, convId, context);
            }
        }

        // 3. Fallback to resilient rule-based client if LLM failed or was unconfigured
        if (response == null) {
            log.debug("Using RuleBasedLlmClient for request");
            response = ruleBasedClient.processChat(userMsg, convId, context);
        }

        return response;
    }

    public void clearConversation(String conversationId) {
        memoryService.clearConversation(conversationId);
    }
}
