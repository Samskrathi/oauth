package com.example.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private static final RequestPostProcessor READ = jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments.read"));
    private static final RequestPostProcessor WRITE = jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments.write"));

    private static final String VALID = """
            {"payerId":"user-1","payeeId":"merchant-1","amount":49.99,
             "currency":"USD","method":"CREDIT_CARD","description":"Order #42"}
            """;

    @Test
    void fullLifecycle() throws Exception {
        String body = mvc.perform(post("/api/payments").with(WRITE).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(body);
        String id = node.get("id").asText();

        mvc.perform(post("/api/payments/{id}/process", id).with(WRITE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.transactionReference").exists());

        mvc.perform(post("/api/payments/{id}/cancel", id).with(WRITE))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/payments/{id}/refund", id).with(WRITE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void rejectsInvalidRequest() throws Exception {
        mvc.perform(post("/api/payments").with(WRITE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payerId\":\"\",\"amount\":-5,\"currency\":\"usd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void returns404ForUnknownPayment() throws Exception {
        mvc.perform(get("/api/payments/{id}", "00000000-0000-0000-0000-000000000000").with(READ))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mvc.perform(get("/api/payments"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readScopeCannotWrite() throws Exception {
        mvc.perform(get("/api/payments").with(READ))
                .andExpect(status().isOk());
        mvc.perform(post("/api/payments").with(READ).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
    }

    @Test
    void writeScopeCannotRead() throws Exception {
        mvc.perform(get("/api/payments").with(WRITE))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
