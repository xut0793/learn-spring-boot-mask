package com.learn.mask.demo;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MaskingApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MaskingProperties properties;

    @Test
    void idCardRuleKeepsSixPrefix() {
        assertThat(properties.ruleOf(SensitiveType.ID_CARD).keepPrefix()).isEqualTo(6);
        assertThat(properties.ruleOf(SensitiveType.ID_CARD).keepSuffix()).isEqualTo(4);
    }

    @Test
    void jacksonMasksForUser() throws Exception {
        mockMvc.perform(get("/api/jackson/users/1")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("138****5678"))
                .andExpect(jsonPath("$.idCard").value("110101********8515"))
                .andExpect(jsonPath("$.email").value("z*******@example.com"))
                .andExpect(jsonPath("$.bankCard").value("6222***********0123"))
                .andExpect(jsonPath("$.address.detail").value("Chaoyang Road **"))
                .andExpect(jsonPath("$.expressNo").value("SF12*******0123"))
                .andExpect(jsonPath("$.contacts[0].value").value("139****1111"));
    }

    @Test
    void jacksonBypassesForAdmin() throws Exception {
        mockMvc.perform(get("/api/jackson/users/1")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("13812345678"))
                .andExpect(jsonPath("$.address.detail").value("Chaoyang Road 88"))
                .andExpect(jsonPath("$.expressNo").value("SF1234567890123"));
    }

    @Test
    void headerRoleOverridesMasking() throws Exception {
        mockMvc.perform(get("/api/jackson/users/1")
                        .header("X-User-Role", "ADMIN")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("13812345678"));
    }

    @Test
    void jacksonMapMasksNestedKeys() throws Exception {
        mockMvc.perform(get("/api/jackson/users/1/as-map")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("138****5678"))
                .andExpect(jsonPath("$.address.detail").value("Chaoyang Road **"))
                .andExpect(jsonPath("$.expressNo").value("SF12*******0123"))
                .andExpect(jsonPath("$.contacts[0].phone").value("139****1111"));
    }

    @Test
    void aopChannelMasksInMemory() throws Exception {
        mockMvc.perform(get("/api/aop/users/1")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("138****5678"))
                .andExpect(jsonPath("$.expressNo").value("SF12*******0123"));
    }

    @Test
    void dbChannelMasksWithoutJacksonAnnotations() throws Exception {
        mockMvc.perform(get("/api/db/users/1")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("user", "user123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("138****5678"))
                .andExpect(jsonPath("$.email").value("z*******@example.com"))
                .andExpect(jsonPath("$.expressNo").value("SF12*******0123"))
                .andExpect(jsonPath("$.addressDetail").value("Chaoyang Road **"));
    }

    @Test
    void csCanUnmaskPlaintext() throws Exception {
        mockMvc.perform(post("/api/unmask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"field\":\"phone\"}")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("cs", "cs123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("13812345678"))
                .andExpect(jsonPath("$.token").value(not("13812345678")));
    }

    @Test
    void userCannotUnmask() throws Exception {
        mockMvc.perform(post("/api/unmask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"field\":\"phone\"}")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("user", "user123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void csCannotUnmaskEmail() throws Exception {
        mockMvc.perform(post("/api/unmask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"field\":\"email\"}")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("cs", "cs123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reloadChangesPhoneMaskThenRestores() throws Exception {
        mockMvc.perform(post("/api/admin/masking/reload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rules\":{\"PHONE\":{\"keepPrefix\":2,\"keepSuffix\":2}}}")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                .andExpect(status().isOk());
        try {
            mockMvc.perform(get("/api/jackson/users/1")
                            .with(SecurityMockMvcRequestPostProcessors.httpBasic("user", "user123")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phone").value("13*******78"));
        } finally {
            mockMvc.perform(post("/api/admin/masking/reload")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rules\":{\"PHONE\":{\"keepPrefix\":3,\"keepSuffix\":4,\"maskChar\":\"*\",\"enabled\":true}}}")
                            .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin123")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void dialTokenDoesNotContainPlaintextAndCanBeRedeemed() throws Exception {
        String body = mockMvc.perform(post("/api/unmask/dial")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"field\":\"phone\"}")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("cs", "cs123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(not("13812345678")))
                .andExpect(jsonPath("$.value").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(post("/api/unmask/redeem-dial")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("cs", "cs123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("13812345678"));
    }

    @Test
    void viewTokenCanRefresh() throws Exception {
        String body = mockMvc.perform(post("/api/unmask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"field\":\"phone\"}")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("cs", "cs123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("13812345678"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(post("/api/unmask/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\"}")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("cs", "cs123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("13812345678"));
    }

    @Test
    void jacksonDisabledPassesThrough() throws Exception {
        boolean original = properties.getChannels().isJackson();
        properties.getChannels().setJackson(false);
        try {
            mockMvc.perform(get("/api/jackson/users/1")
                            .with(SecurityMockMvcRequestPostProcessors.httpBasic("user", "user123")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phone").value("13812345678"));
        } finally {
            properties.getChannels().setJackson(original);
        }
    }
}
