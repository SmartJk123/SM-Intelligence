package io.smartmoney.api;

import io.smartmoney.api.config.PlatformProperties;
import io.smartmoney.api.config.PlatformSettingRepository;
import io.smartmoney.api.config.PublicBaseUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The webhook base address is editable from the admin interface and survives a restart. */
@SpringBootTest
class PublicBaseUrlTests {

    @Autowired private WebApplicationContext context;
    @Autowired private PlatformProperties platform;
    @Autowired private PlatformSettingRepository settings;
    @Autowired private PublicBaseUrlService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void savesTheAddressAndEveryWebhookUrlFollowsIt() throws Exception {
        String original = platform.publicBaseUrl();
        try {
            mvc.perform(put("/api/v1/admin/bank-integrations/platform/public-base-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"publicBaseUrl\":\"https://sm-intelligence.globalsmartspaces.com/api/v1/webhooks/ncba\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.publicBaseUrl").value("https://sm-intelligence.globalsmartspaces.com"))
                    .andExpect(jsonPath("$.callbackIsPublic").value(true));
            mvc.perform(get("/api/v1/admin/bank-integrations/kcb/webhook-url"))
                    .andExpect(jsonPath("$.webhookUrl").value("https://sm-intelligence.globalsmartspaces.com/api/v1/webhooks/kcb"));

            // A restart starts from the environment value, then applies the saved one.
            platform.setPublicBaseUrl("http://localhost:8080");
            service.applySaved();
            assertThat(platform.publicBaseUrl()).isEqualTo("https://sm-intelligence.globalsmartspaces.com");

            mvc.perform(put("/api/v1/admin/bank-integrations/platform/public-base-url")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"publicBaseUrl\":\"ftp://nope\"}"))
                    .andExpect(status().isBadRequest());
        } finally {
            settings.deleteAll();
            platform.setPublicBaseUrl(original);
        }
    }
}
