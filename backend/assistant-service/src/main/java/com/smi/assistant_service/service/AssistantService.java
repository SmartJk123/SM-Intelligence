package com.smi.assistant_service.service;

import com.smi.assistant_service.dto.RahaChatRequest;
import com.smi.assistant_service.dto.RahaChatResponse;
import com.smi.assistant_service.dto.RahaPageBriefingRequest;
import com.smi.assistant_service.llm.DeepSeekLlmClient;
import com.smi.assistant_service.llm.GeminiLlmClient;
import com.smi.assistant_service.llm.RuleBasedLlmClient;
import com.smi.assistant_service.model.FinancialContext;
import com.smi.assistant_service.model.ScreenContext;
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

        if (request.getScreenRoute() != null && !request.getScreenRoute().isBlank()) {
            context.setScreenContext(new ScreenContext(
                    request.getScreenRoute(),
                    request.getScreenTitle(),
                    request.getPageData()
            ));
        }

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

    public RahaChatResponse getPageBriefing(RahaPageBriefingRequest request, String authorization) {
        FinancialContext context = contextAggregator.aggregateContext(authorization, request.getClientInvestments());

        context.setScreenContext(new ScreenContext(
                request.getScreenRoute(),
                request.getScreenTitle(),
                request.getPageData()
        ));

        String convId = request.getConversationId();
        RahaChatResponse response = null;

        if ("deepseek".equals(preferredProvider)) {
            if (deepSeekClient.isConfigured()) {
                response = deepSeekClient.generatePageBriefing(convId, context);
            }
        } else if ("gemini".equals(preferredProvider)) {
            if (geminiClient.isConfigured()) {
                response = geminiClient.generatePageBriefing(convId, context);
            }
        } else if ("auto".equals(preferredProvider)) {
            if (geminiClient.isConfigured()) {
                response = geminiClient.generatePageBriefing(convId, context);
            }
            if (response == null && deepSeekClient.isConfigured()) {
                response = deepSeekClient.generatePageBriefing(convId, context);
            }
        }

        if (response == null) {
            log.debug("Using RuleBasedLlmClient for page briefing");
            response = ruleBasedClient.generatePageBriefing(convId, context);
        }

        return response;
    }

    public String getPromptNudge(String screenRoute, String authorization) {
        String route = screenRoute != null ? screenRoute.toLowerCase().trim() : "dashboard";
        switch (route) {
            case "dashboard":
            case "overview":
                return "Ask Raha: What is my total net worth today? ✨";
            case "accounts":
                return "Ask Raha: Break down my bank & liquid cash ✨";
            case "transactions":
                return "Ask Raha: Where did most of my money go? ✨";
            case "budgets":
                return "Ask Raha: Am I on track with my spending limits? ✨";
            case "investments":
                return "Ask Raha: How are my MMF & M-Shwari yields? ✨";
            case "cashflow":
                return "Ask Raha: How is my monthly savings flow trending? ✨";
            case "analysis":
            case "analytics":
                return "Ask Raha: How does my spending match 50/30/20? ✨";
            case "reports":
                return "Ask Raha: Generate my monthly financial statement ✨";
            case "notifications":
                return "Ask Raha: Review my recent SMS transaction alerts ✨";
            case "settings":
                return "Ask Raha: Is my biometric & PIN security active? ✨";
            case "invoice":
                return "Ask Raha: Help me scan a receipt with camera OCR ✨";
            case "menu":
                return "Ask Raha: Guide me to any feature in SmartMoney ✨";
            default:
                return "Ask Raha: Need financial insights or balance check? ✨";
        }
    }

    public void clearConversation(String conversationId) {
        memoryService.clearConversation(conversationId);
    }
}
