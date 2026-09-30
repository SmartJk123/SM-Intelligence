package com.smi.identity_service.controller;

import com.smi.identity_service.domain.User;
import com.smi.identity_service.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminUserController adminUserController;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(adminUserController).build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/users - Returns every active user without the password hash")
    void shouldListActiveUsers() throws Exception {
        User individual = new User(UUID.randomUUID(), "Jane Doe", "jane@example.com", "hashed_pwd");
        individual.setAccountType("INDIVIDUAL");
        User organization = new User(UUID.randomUUID(), "James Otieno", "james@example.com", "hashed_pwd");
        organization.setAccountType("ORGANIZATION");
        organization.setOrganizationName("Kilimani Properties");

        when(userService.listActiveUsers()).thenReturn(List.of(individual, organization));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Jane Doe"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[1].organizationName").value("Kilimani Properties"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users - No users returns an empty list, not an error")
    void shouldReturnEmptyListWhenNoUsers() throws Exception {
        when(userService.listActiveUsers()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }
}
