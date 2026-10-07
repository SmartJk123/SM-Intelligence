package com.smi.assistant_service.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class RahaChatRequest {

    @NotBlank(message = "Message must not be blank")
    private String message;

    private String conversationId;

    private List<RahaInvestmentDto> clientInvestments = new ArrayList<>();

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
}
