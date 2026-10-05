package com.smi.identity_service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smi.identity_service.repository.OrganizationMemberRepository;
import com.smi.identity_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Real HTTP, security, composite-key persistence (H2). No mail server is configured
// in tests, so emailSent must come back false while everything is still written.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrganizationIntegrationTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired OrganizationMemberRepository members;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    private String adminToken;

    /** Organisation endpoints need a platform admin; the test config lists admin@example.invalid. */
    private String adminToken() throws Exception {
        if (adminToken == null) {
            var credentials = Map.of("emailAddress", "admin@example.invalid", "password", "AdminPassword123!");
            send("/api/auth/register", Map.of("name", "Platform Admin", "emailAddress", "admin@example.invalid",
                "password", "AdminPassword123!"), null);
            adminToken = json.readTree(send("/api/auth/login", credentials, null).body()).get("token").asText();
        }
        return adminToken;
    }

    private HttpResponse<String> send(String route, Map<String, String> body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + route))
            .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String route, Map<String, String> body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + route))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + adminToken())
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String route) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + route))
            .header("Authorization", "Bearer " + adminToken()).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void createOrganisationThenInviteMembers() throws Exception {
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.invalid";
        var created = post("/api/organizations", Map.of(
            "organizationName", "Kilimani Properties " + UUID.randomUUID(),
            "ownerName", "James Otieno", "ownerEmail", ownerEmail, "businessType", "SME"));
        assertEquals(201, created.statusCode(), created.body());
        JsonNode org = json.readTree(created.body());
        String orgId = org.get("id").asText();
        assertEquals(1, org.get("memberCount").asInt());
        assertFalse(org.get("emailSent").asBoolean());
        assertFalse(created.body().toLowerCase().contains("password"));

        var owner = users.findByEmailAddressAndDeletedAtIsNull(ownerEmail).orElseThrow();
        var ownerMembership = members.findById(new com.smi.identity_service.domain.OrganizationMemberId(
            UUID.fromString(orgId), owner.getId())).orElseThrow();
        assertEquals("OWNER", ownerMembership.getRole());

        assertTrue(get("/api/organizations").body().contains(orgId));

        String memberEmail = "member-" + UUID.randomUUID() + "@example.invalid";
        var invite = Map.of("name", "Jane Doe", "email", memberEmail, "role", "ADMIN");
        var invited = post("/api/organizations/" + orgId + "/members", invite);
        assertEquals(201, invited.statusCode(), invited.body());
        assertEquals("ADMIN", json.readTree(invited.body()).get("role").asText());
        assertEquals(409, post("/api/organizations/" + orgId + "/members", invite).statusCode());

        var membersList = json.readTree(get("/api/organizations/" + orgId + "/members").body());
        assertEquals(2, membersList.size());

        assertEquals(400, post("/api/organizations/" + orgId + "/members",
            Map.of("name", "Bad Role", "email", "x@example.invalid", "role", "SUPERUSER")).statusCode());
        assertEquals(404, post("/api/organizations/" + UUID.randomUUID() + "/members", invite).statusCode());
    }

    @Test
    void organisationEndpointsRefuseAnyoneButAPlatformAdmin() throws Exception {
        var anonymous = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/organizations")).build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonymous.statusCode());

        String email = "customer-" + UUID.randomUUID() + "@example.invalid";
        String customerToken = json.readTree(send("/api/auth/register", Map.of("name", "Customer",
            "emailAddress", email, "password", "CustomerPassword123!"), null).body()).get("token").asText();
        var customer = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/organizations"))
            .header("Authorization", "Bearer " + customerToken).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, customer.statusCode());
    }
}
