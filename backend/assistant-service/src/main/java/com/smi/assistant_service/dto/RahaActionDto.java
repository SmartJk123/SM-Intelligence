package com.smi.assistant_service.dto;

import java.util.Map;

public class RahaActionDto {

    private String type;           // e.g. "NAVIGATE", "SHOW_SUMMARY", "RECOMMEND_BUDGET"
    private String targetRoute;    // e.g. "budgets", "accounts", "invoice", "transactions", "investments"
    private Map<String, String> payload;

    public RahaActionDto() {
    }

    public RahaActionDto(String type, String targetRoute, Map<String, String> payload) {
        this.type = type;
        this.targetRoute = targetRoute;
        this.payload = payload;
    }

    public static RahaActionDto navigate(String targetRoute) {
        return new RahaActionDto("NAVIGATE", targetRoute, null);
    }

    public static RahaActionDto navigate(String targetRoute, Map<String, String> payload) {
        return new RahaActionDto("NAVIGATE", targetRoute, payload);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTargetRoute() {
        return targetRoute;
    }

    public void setTargetRoute(String targetRoute) {
        this.targetRoute = targetRoute;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, String> payload) {
        this.payload = payload;
    }
}
