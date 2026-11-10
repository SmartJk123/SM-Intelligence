package com.smi.assistant_service.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RahaChatRequest {

    @NotBlank(message = "Message must not be blank")
    private String message;

    private String conversationId;

    private List<RahaInvestmentDto> clientInvestments = new ArrayList<>();

    private String screenRoute;

    private String screenTitle;

    private Map<String, Object> pageData = new HashMap<>();

    public RahaChatRequest() {
    }

    public RahaChatRequest(String message, String conversationId) {
        this.message = message;
        this.conversationId = conversationId;
    }

    public RahaChatRequest(String message, String conversationId, List<RahaInvestmentDto> clientInvestments) {
        this.message = message;
        this.conversationId = conversationId;
        this.clientInvestments = clientInvestments != null ? clientInvestments : new ArrayList<>();
    }

    public RahaChatRequest(
            String message,
            String conversationId,
            List<RahaInvestmentDto> clientInvestments,
            String screenRoute,
            String screenTitle,
            Map<String, Object> pageData) {
        this.message = message;
        this.conversationId = conversationId;
        this.clientInvestments = clientInvestments != null ? clientInvestments : new ArrayList<>();
        this.screenRoute = screenRoute;
        this.screenTitle = screenTitle;
        this.pageData = pageData != null ? pageData : new HashMap<>();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public List<RahaInvestmentDto> getClientInvestments() {
        return clientInvestments;
    }

    public void setClientInvestments(List<RahaInvestmentDto> clientInvestments) {
        this.clientInvestments = clientInvestments != null ? clientInvestments : new ArrayList<>();
    }

    public String getScreenRoute() {
        return screenRoute;
    }

    public void setScreenRoute(String screenRoute) {
        this.screenRoute = screenRoute;
    }

    public String getScreenTitle() {
        return screenTitle;
    }

    public void setScreenTitle(String screenTitle) {
        this.screenTitle = screenTitle;
    }

    public Map<String, Object> getPageData() {
        return pageData;
    }

    public void setPageData(Map<String, Object> pageData) {
        this.pageData = pageData != null ? pageData : new HashMap<>();
    }
}
