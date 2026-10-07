package com.smi.assistant_service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smi.assistant_service.dto.RahaActionDto;
import com.smi.assistant_service.dto.RahaChatResponse;
import com.smi.assistant_service.model.AccountSummary;
import com.smi.assistant_service.model.BudgetSummary;
import com.smi.assistant_service.model.FinancialContext;
import com.smi.assistant_service.model.TransactionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DeepSeekLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmClient.class);

    private final String apiKey;
    private final String model;
    private final String endpoint;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DeepSeekLlmClient(
            @Value("${raha.deepseek.api-key:}") String apiKey,
            @Value("${raha.deepseek.model:deepseek-chat}") String model,
            @Value("${raha.deepseek.endpoint:https://api.deepseek.com/chat/completions}") String endpoint,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : "deepseek-chat";
        this.endpoint = endpoint;
        this.objectMapper = objectMapper;

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        );
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    @Override
    public RahaChatResponse processChat(String userMessage, String conversationId, FinancialContext context) {
        String convId = conversationId != null && !conversationId.isBlank()
                ? conversationId
                : UUID.randomUUID().toString();

        try {
            // Build OpenAI-compatible chat completion payload
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", model);
            root.put("temperature", 0.3);

            // Response format json_object
            ObjectNode responseFormat = root.putObject("response_format");
            responseFormat.put("type", "json_object");

            ArrayNode messages = root.putArray("messages");

            // System prompt
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", buildSystemPrompt(context));

            // User prompt
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            String requestBody = objectMapper.writeValueAsString(root);

            String responseBody = restClient.post()
                    .uri(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("Empty response from DeepSeek API");
                return null;
            }

            JsonNode responseJson = objectMapper.readTree(responseBody);
            JsonNode choices = responseJson.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String rawContent = choices.get(0).path("message").path("content").asText();
                return parseLlmResponse(rawContent, convId);
            }
        } catch (Exception e) {
            log.error("Error calling DeepSeek API: {}", e.getMessage(), e);
        }

        return null;
    }

    private RahaChatResponse parseLlmResponse(String rawText, String conversationId) {
        try {
            String cleaned = rawText.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            JsonNode parsed = objectMapper.readTree(cleaned);

            String replyMessage = parsed.path("replyMessage").asText("I'm here to help manage your finances.");

            RahaActionDto action = null;
            JsonNode actionNode = parsed.path("action");
            if (!actionNode.isMissingNode() && !actionNode.isNull()) {
                String type = actionNode.path("type").asText("NONE");
                if (!"NONE".equalsIgnoreCase(type)) {
                    String targetRoute = actionNode.path("targetRoute").asText("");
                    Map<String, String> payload = new HashMap<>();
                    JsonNode payloadNode = actionNode.path("payload");
                    if (payloadNode.isObject()) {
                        payloadNode.fields().forEachRemaining(entry -> payload.put(entry.getKey(), entry.getValue().asText()));
                    }
                    action = new RahaActionDto(type, targetRoute, payload);
                }
            }

            List<String> quickReplies = new ArrayList<>();
            JsonNode qrNode = parsed.path("quickReplies");
            if (qrNode.isArray()) {
                for (JsonNode qr : qrNode) {
                    quickReplies.add(qr.asText());
                }
            }
            if (quickReplies.isEmpty()) {
                quickReplies.addAll(List.of("Check my Balance", "My Budgets", "Recent Expenses"));
            }

            return new RahaChatResponse(conversationId, replyMessage, action, quickReplies);
        } catch (Exception e) {
            log.warn("Failed to parse DeepSeek response as structured JSON: {}. Using raw text.", rawText);
            return new RahaChatResponse(conversationId, rawText, null, List.of("Check my Balance", "Budgets"));
        }
    }

    private String buildSystemPrompt(FinancialContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Raha, the personal AI financial assistant for SmartMoney (Kenya & East Africa, currency KES).\n");
        sb.append("Personality: Friendly, encouraging, polite (occasional warm Swahili greeting like 'Jambo' or 'Karibu'), concise (1-3 sentences).\n");
        sb.append("Security rule: Never invent account numbers, passwords, PINs, or disclose sensitive PII.\n\n");

        sb.append("User's sanitized financial snapshot:\n");
        sb.append("- User First Name: ").append(context.getUserName()).append("\n");
        sb.append("- Total Liquid Balance: KES ").append(context.getTotalBalance()).append("\n");

        if (context.getAccounts() != null && !context.getAccounts().isEmpty()) {
            sb.append("- Connected Bank Accounts:\n");
            for (AccountSummary acc : context.getAccounts()) {
                sb.append("  * ").append(acc.getInstitution()).append(" (").append(acc.getAccountType()).append("): KES ").append(acc.getBalance()).append("\n");
            }
        }

        if (context.getBudgets() != null && !context.getBudgets().isEmpty()) {
            sb.append("- Active Spending Budgets:\n");
            for (BudgetSummary b : context.getBudgets()) {
                sb.append("  * ").append(b.getCategory()).append(": Limit KES ").append(b.getLimitAmount()).append("\n");
            }
        }

        if (context.getRecentTransactions() != null && !context.getRecentTransactions().isEmpty()) {
            sb.append("- Recent Activity:\n");
            for (TransactionSummary tx : context.getRecentTransactions()) {
                sb.append("  * ").append(tx.getDescription()).append(" - KES ").append(tx.getAmount()).append("\n");
            }
        }

        sb.append("\nOutput format requirement:\n");
        sb.append("Return ONLY a JSON object matching this schema:\n");
        sb.append("{\n");
        sb.append("  \"replyMessage\": \"Concise and helpful conversational reply\",\n");
        sb.append("  \"action\": {\n");
        sb.append("    \"type\": \"NAVIGATE\" or \"NONE\",\n");
        sb.append("    \"targetRoute\": \"accounts\" | \"budgets\" | \"transactions\" | \"invoice\" | \"investments\" | \"send_money\",\n");
        sb.append("    \"payload\": {}\n");
        sb.append("  },\n");
        sb.append("  \"quickReplies\": [\"Option 1\", \"Option 2\", \"Option 3\"]\n");
        sb.append("}\n");

        return sb.toString();
    }
}
