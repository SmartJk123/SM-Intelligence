package com.smi.assistant_service.dto;

import java.util.ArrayList;
import java.util.List;

public class RahaChatResponse {

    private String conversationId;
    private String replyMessage;
    private RahaActionDto action;
    private List<String> quickReplies = new ArrayList<>();

    public RahaChatResponse() {
    }

    public RahaChatResponse(String conversationId, String replyMessage, RahaActionDto action, List<String> quickReplies) {
        this.conversationId = conversationId;
        this.replyMessage = replyMessage;
        this.action = action;
        this.quickReplies = quickReplies != null ? quickReplies : new ArrayList<>();
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getReplyMessage() {
        return replyMessage;
    }

    public void setReplyMessage(String replyMessage) {
        this.replyMessage = replyMessage;
    }

    public RahaActionDto getAction() {
        return action;
    }

    public void setAction(RahaActionDto action) {
        this.action = action;
    }

    public List<String> getQuickReplies() {
        return quickReplies;
    }

    public void setQuickReplies(List<String> quickReplies) {
        this.quickReplies = quickReplies != null ? quickReplies : new ArrayList<>();
    }
}
