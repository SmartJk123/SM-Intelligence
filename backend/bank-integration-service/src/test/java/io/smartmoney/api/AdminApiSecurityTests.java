package io.smartmoney.api;

import io.smartmoney.api.accountlink.PlatformServicesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
<<<<<<< HEAD
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
=======
>>>>>>> 33f848efaf0a872418c1f0e9953c50840df45da6
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin API with security on, as it runs outside tests. Tokens are built
 * the way identity-service's jjwt builds them: HMAC over the UTF-8 bytes of
 * JWT_SECRET, HS512 for a 64 character secret.
 */
@SpringBootTest(properties = {
        "smartmoney.security.permit-all=false",
        "smartmoney.security.jwt-secret=" + AdminApiSecurityTests.SECRET})
class AdminApiSecurityTests {

    static final String SECRET = "test-only-shared-admin-secret-0123456789-0123456789-0123456789ab";
    private static final String CUSTOMER = "8b0c0773-122c-4aa8-8ed8-725d85287ed7";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private PlatformServicesClient platform;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static String token(String secret, String subject, String role, long expiresInSeconds) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"HS512\"}".getBytes(StandardCharsets.UTF_8));
        long exp = System.currentTimeMillis() / 1000 + expiresInSeconds;
        String claims = encoder.encodeToString(("{\"sub\":\"" + subject + "\",\"role\":\""
                + role + "\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        String signature = encoder.encodeToString(
                mac.doFinal((header + "." + claims).getBytes(StandardCharsets.US_ASCII)));
        return header + "." + claims + "." + signature;
    }

    private static String bearer(String role) throws Exception {
        return "Bearer " + token(SECRET, CUSTOMER, role, 600);
    }

    @Test
    void adminApiNeedsAnAdminTokenFromIdentityService() throws Exception {
        String path = "/api/v1/admin/bank-integrations";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", bearer("USER"))).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", "Bearer " + token(SECRET, CUSTOMER, "PLATFORM_ADMIN", -10)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization",
                        "Bearer " + token(SECRET.replace('a', 'b'), CUSTOMER, "PLATFORM_ADMIN", 600)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer not.a.token")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", bearer("PLATFORM_ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void banksAndMonitorsStillReachTheirEndpointsWithoutAToken() throws Exception {
        mvc.perform(get("/api/v1/webhooks/ncba")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void aCustomerCanLinkOnlyAnAccountTheySavedThemselves() throws Exception {
        String body = """
                {"bankId":"kcb","accountNumber":"1302360167","userId":"%s","accountName":"Mine","accountId":"%s"}
                """;
        String accountId = "1f0e2d3c-4b5a-6978-8a9b-0c1d2e3f4a5b";

        // Another customer's user id.
        mvc.perform(post("/api/v1/admin/account-links").header("Authorization", bearer("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted("00000000-0000-0000-0000-000000000001", accountId)))
                .andExpect(status().isForbidden());

        // Their own id, but accounts-service does not hold that bank and number for them.
        when(platform.ownsAccount(eq(CUSTOMER), eq(accountId), eq("KCB"), eq("1302360167"))).thenReturn(false);
        mvc.perform(post("/api/v1/admin/account-links").header("Authorization", bearer("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(CUSTOMER, accountId)))
                .andExpect(status().isForbidden());

        // Their own saved account.
        when(platform.ownsAccount(eq(CUSTOMER), eq(accountId), eq("KCB"), eq("1302360167"))).thenReturn(true);
        mvc.perform(post("/api/v1/admin/account-links").header("Authorization", bearer("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(CUSTOMER, accountId)))
                .andExpect(status().isCreated());

        // Every other admin endpoint stays admin only.
        mvc.perform(get("/api/v1/admin/account-links").header("Authorization", bearer("USER")))
                .andExpect(status().isForbidden());
    }
<<<<<<< HEAD

    @Test
    void aCustomerCanRemoveOnlyTheirOwnLink() throws Exception {
        String otherCustomer = "00000000-0000-0000-0000-000000000002";
        String othersAccount = "2a1b0c9d-8e7f-6a5b-4c3d-2e1f0a9b8c7d";
        when(platform.ensureAccount(eq(otherCustomer), eq("KCB"), eq("5566778899"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(othersAccount);
        // An admin links an account to another customer.
        mvc.perform(post("/api/v1/admin/account-links").header("Authorization", bearer("PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankId\":\"kcb\",\"accountNumber\":\"5566778899\",\"userId\":\""
                                + otherCustomer + "\",\"accountName\":\"Theirs\"}"))
                .andExpect(status().isCreated());

        // This customer cannot remove it.
        mvc.perform(delete("/api/v1/admin/account-links/by-account/" + othersAccount)
                        .header("Authorization", bearer("USER")))
                .andExpect(status().isForbidden());
        // No token at all is refused too.
        mvc.perform(delete("/api/v1/admin/account-links/by-account/" + othersAccount))
                .andExpect(status().isUnauthorized());
        // An account with no links is a harmless no-op for its owner.
        mvc.perform(delete("/api/v1/admin/account-links/by-account/3b2c1d0e-9f8a-7b6c-5d4e-3f2a1b0c9d8e")
                        .header("Authorization", bearer("USER")))
                .andExpect(status().isOk());
        // The other customer's owner (here, an admin) can remove it.
        mvc.perform(delete("/api/v1/admin/account-links/by-account/" + othersAccount)
                        .header("Authorization", bearer("PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.removed").value(1));
    }
=======
>>>>>>> 33f848efaf0a872418c1f0e9953c50840df45da6
}
