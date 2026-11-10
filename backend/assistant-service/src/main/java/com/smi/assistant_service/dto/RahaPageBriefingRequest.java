package com.smi.assistant_service.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RahaPageBriefingRequest {

    @NotBlank(message = "Screen route must not be blank")
    private String screenRoute;

    private String screenTitle;

    private String conversationId;

    private Map<String, Object> pageData = new HashMap<>();

    private List<RahaInvestmentDto> clientInvestments = new ArrayList<>();

    public RahaPageBriefingRequest() {
    }

    public RahaPageBriefingRequest(String screenRoute, String screenTitle, String conversationId) {
        this.screenRoute = screenRoute;
        this.screenTitle = screenTitle;
        this.conversationId = conversationId;
    }

    public RahaPageBriefingRequest(
            String screenRoute,
            String screenTitle,
            String conversationId,
            Map<String, Object> pageData,
            List<RahaInvestmentDto> clientInvestments) {
        this.screenRoute = screenRoute;
        this.screenTitle = screenTitle;
        this.conversationId = conversationId;
        this.pageData = pageData != null ? pageData : new HashMap<>();
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

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Map<String, Object> getPageData() {
        return pageData;
    }

    public void setPageData(Map<String, Object> pageData) {
        this.pageData = pageData != null ? pageData : new HashMap<>();
    }

    public List<RahaInvestmentDto> getClientInvestments() {
        return clientInvestments;
    }

    public void setClientInvestments(List<RahaInvestmentDto> clientInvestments) {
        this.clientInvestments = clientInvestments != null ? clientInvestments : new ArrayList<>();
    }
}
