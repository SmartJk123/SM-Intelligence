package com.smi.audit_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smi.audit_service.controller.AuditController;
import com.smi.audit_service.domain.AuditLog;
import com.smi.audit_service.dto.CreateAuditLogRequest;
import com.smi.audit_service.exception.GlobalExceptionHandler;
import com.smi.audit_service.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuditIntegrationTest {

    @Autowired
    private AuditController auditController;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(auditController)
                .setControllerAdvice(globalExceptionHandler)
                .build();
        auditLogRepository.deleteAll();
    }

    @Test
    void recordAuditLog_Success() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        CreateAuditLogRequest request = new CreateAuditLogRequest(
                "accounts-service",
                "accounts",
                recordId,
                "INSERT",
                null,
                "{\"balance\": 250000.00, \"currency\": \"KES\"}",
                actorId
        );

        mockMvc.perform(post("/api/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sourceService").value("accounts-service"))
                .andExpect(jsonPath("$.tableName").value("accounts"))
                .andExpect(jsonPath("$.recordId").value(recordId.toString()))
                .andExpect(jsonPath("$.action").value("INSERT"))
                .andExpect(jsonPath("$.newData").value("{\"balance\": 250000.00, \"currency\": \"KES\"}"))
                .andExpect(jsonPath("$.changedBy").value(actorId.toString()))
                .andExpect(jsonPath("$.changedAt").exists());

        assertThat(auditLogRepository.count()).isEqualTo(1);
    }

    @Test
    void getEntityHistory_ReturnsChronologicalLogs() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        AuditLog insertLog = new AuditLog(
                "transactions-service",
                "transactions",
                recordId,
                "INSERT",
                null,
                "{\"amount\": 5000.00, \"status\": \"PENDING\"}",
                actorId
        );
        AuditLog updateLog = new AuditLog(
                "transactions-service",
                "transactions",
                recordId,
                "UPDATE",
                "{\"amount\": 5000.00, \"status\": \"PENDING\"}",
                "{\"amount\": 5000.00, \"status\": \"COMPLETED\"}",
                actorId
        );

        auditLogRepository.save(insertLog);
        auditLogRepository.save(updateLog);

        mockMvc.perform(get("/api/audit/history/transactions/" + recordId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("UPDATE"))
                .andExpect(jsonPath("$[1].action").value("INSERT"));
    }

    @Test
    void getAuditLogs_WithFilteringAndPagination() throws Exception {
        UUID recordId = UUID.randomUUID();

        auditLogRepository.save(new AuditLog("accounts-service", "accounts", UUID.randomUUID(), "INSERT", null, "{}", null));
        auditLogRepository.save(new AuditLog("bank-integration-service", "linked_institutions", UUID.randomUUID(), "INSERT", null, "{}", null));
        auditLogRepository.save(new AuditLog("accounts-service", "accounts", recordId, "UPDATE", "{}", "{}", null));

        mockMvc.perform(get("/api/audit")
                        .param("sourceService", "accounts-service")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].sourceService").value("accounts-service"))
                .andExpect(jsonPath("$.content[1].sourceService").value("accounts-service"));
    }

    @Test
    void getAuditLogById_SuccessAndNotFound() throws Exception {
        AuditLog log = auditLogRepository.save(new AuditLog(
                "identity-service", "users", UUID.randomUUID(), "INSERT", null, "{}", null));

        mockMvc.perform(get("/api/audit/" + log.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(log.getId().toString()))
                .andExpect(jsonPath("$.tableName").value("users"));

        mockMvc.perform(get("/api/audit/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void recordAuditLog_ValidationErrors() throws Exception {
        // Missing required fields (blank sourceService, invalid action)
        String invalidJson = """
                {
                    "sourceService": "",
                    "tableName": "accounts",
                    "recordId": "%s",
                    "action": "INVALID_ACTION"
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.sourceService").exists())
                .andExpect(jsonPath("$.validationErrors.action").exists());
    }

    @Test
    void recordAuditLog_InvalidUUIDReturns400() throws Exception {
        String invalidUuidJson = """
                {
                    "sourceService": "accounts-service",
                    "tableName": "accounts",
                    "recordId": "not-a-valid-uuid",
                    "action": "INSERT"
                }
                """;

        mockMvc.perform(post("/api/audit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidUuidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }
}
