package com.smi.assistant_service.controller;

import com.smi.assistant_service.dto.RahaChatRequest;
import com.smi.assistant_service.dto.RahaChatResponse;
import com.smi.assistant_service.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public ResponseEntity<RahaChatResponse> chat(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RahaChatRequest request) {
        RahaChatResponse response = assistantService.chat(request, authorization);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> clearConversation(@PathVariable("id") String id) {
        assistantService.clearConversation(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "assistant", "Raha",
                "version", "1.0.0"
        ));
    }
}
