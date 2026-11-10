package com.smi.notifications_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smi.notifications_service.domain.Notification;
import com.smi.notifications_service.domain.NotificationPreference;
import com.smi.notifications_service.dto.CreateNotificationRequest;
import com.smi.notifications_service.dto.UpdatePreferenceRequest;
import com.smi.notifications_service.repository.NotificationOutboxRepository;
import com.smi.notifications_service.repository.NotificationPreferenceRepository;
import com.smi.notifications_service.repository.NotificationRepository;
import com.smi.notifications_service.controller.NotificationController;
import com.smi.notifications_service.controller.PreferenceController;
import com.smi.notifications_service.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class NotificationIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private NotificationController notificationController;

    @Autowired
    private PreferenceController preferenceController;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @Autowired
    private NotificationOutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID userId;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(notificationController, preferenceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        outboxRepository.deleteAll();
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();
        userId = UUID.randomUUID();
    }

    @Test
    void createAndFetchNotificationFlow() throws Exception {
        CreateNotificationRequest createRequest = new CreateNotificationRequest(
                userId,
                "SECURITY_ALERT",
                "CRITICAL",
                "New Login Attempt",
                "Login detected from unknown IP",
                null,
                null,
                "{\"ip\": \"192.168.1.1\"}",
                List.of("EMAIL")
        );

        // 1. Create notification
        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.title").value("New Login Attempt"))
                .andExpect(jsonPath("$.isRead").value(false));

        // Verify outbox entry was queued because email is enabled by default
        assertThat(outboxRepository.findAll()).hasSize(1);
        assertThat(outboxRepository.findAll().get(0).getDeliveryChannel()).isEqualTo("EMAIL");

        // 2. Check unread count
        mockMvc.perform(get("/api/notifications/unread-count")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        // 3. List notifications
        mockMvc.perform(get("/api/notifications")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title").value("New Login Attempt"));

        // 4. Mark notification as read
        Notification notification = notificationRepository.findAll().get(0);
        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId())
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));

        // 5. Unread count should now be 0
        mockMvc.perform(get("/api/notifications/unread-count")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void markAllAsReadFlow() throws Exception {
        notificationRepository.save(new Notification(userId, "TX", "INFO", "Tx 1", "Message 1"));
        notificationRepository.save(new Notification(userId, "TX", "INFO", "Tx 2", "Message 2"));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));

        mockMvc.perform(post("/api/notifications/mark-all-read")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedReadCount").value(2));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void preferencesFlow() throws Exception {
        // Fetch default preferences
        mockMvc.perform(get("/api/notifications/preferences")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("ALL"))
                .andExpect(jsonPath("$[0].inAppEnabled").value(true));

        // Disable in-app notifications for BUDGET_EXCEEDED
        UpdatePreferenceRequest updateReq = new UpdatePreferenceRequest(false, true, true);
        mockMvc.perform(put("/api/notifications/preferences/{eventType}", "BUDGET_EXCEEDED")
                        .param("userId", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("BUDGET_EXCEEDED"))
                .andExpect(jsonPath("$.inAppEnabled").value(false))
                .andExpect(jsonPath("$.pushEnabled").value(true));

        // Create a notification for BUDGET_EXCEEDED - should be auto-marked as read due to preference
        CreateNotificationRequest req = new CreateNotificationRequest(
                userId,
                "BUDGET_EXCEEDED",
                "WARNING",
                "Budget Warning",
                "You reached 90% of your food budget",
                null,
                null,
                null,
                List.of("PUSH")
        );

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isRead").value(true));

        // Verify outbox queued a PUSH message
        assertThat(outboxRepository.findAll()).hasSize(1);
        assertThat(outboxRepository.findAll().get(0).getDeliveryChannel()).isEqualTo("PUSH");
    }

    @Test
    void deleteNotificationFlow() throws Exception {
        Notification n = notificationRepository.save(new Notification(userId, "TX", "INFO", "To Delete", "Msg"));

        mockMvc.perform(delete("/api/notifications/{id}", n.getId())
                        .param("userId", userId.toString()))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.findById(n.getId())).isEmpty();
    }
}
