package io.smartmoney.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.oneOf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** /actuator/health/banks: one indicator per bank, with details only for an admin. */
@SpringBootTest(properties = {
        "smartmoney.security.permit-all=false",
        "smartmoney.security.jwt-secret=" + BankHealthIndicatorTests.SECRET,
        "management.endpoints.web.exposure.include=health"})
class BankHealthIndicatorTests {

    static final String SECRET = "test-only-shared-admin-secret-0123456789-0123456789-0123456789ab";

    @Autowired private WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static String adminToken() throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"HS512\"}".getBytes(StandardCharsets.UTF_8));
        String claims = encoder.encodeToString(("{\"sub\":\"8b0c0773-122c-4aa8-8ed8-725d85287ed7\",\"role\":\"PLATFORM_ADMIN\",\"exp\":"
                + (System.currentTimeMillis() / 1000 + 600) + "}").getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return "Bearer " + header + "." + claims + "."
                + encoder.encodeToString(mac.doFinal((header + "." + claims).getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void anAdminSeesEveryBank() throws Exception {
        mvc.perform(get("/actuator/health/banks").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.kcb.status").exists())
                .andExpect(jsonPath("$.components.ncba.status").exists())
                .andExpect(jsonPath("$.components.stanbic.status").exists())
                .andExpect(jsonPath("$.components.equity.status").exists())
                .andExpect(jsonPath("$.components.equity.details.connectorImplemented").value(true))
                .andExpect(jsonPath("$.components.equity.details.environment").exists());
    }

    @Test
    void anyoneElseSeesOnlyTheOverallStatus() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(oneOf("UP", "UNKNOWN", "DEGRADED")))
                .andExpect(jsonPath("$.components").doesNotExist());
        // The bank breakdown is not even acknowledged to exist without an admin token.
        mvc.perform(get("/actuator/health/banks")).andExpect(status().isNotFound());
    }
}
