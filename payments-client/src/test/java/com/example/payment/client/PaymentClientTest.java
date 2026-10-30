package com.example.payment.client;

import com.example.payment.client.dto.PaymentResponse;
import com.example.payment.client.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PaymentClientTest {

    private MockRestServiceServer server;
    private PaymentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payments");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PaymentClient(builder);
    }

    @Test
    void getsPayment() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://payments/api/payments/" + id))
                .andRespond(withSuccess("{\"id\":\"" + id + "\",\"status\":\"PENDING\",\"amount\":10.50,"
                        + "\"createdAt\":\"2026-10-29T10:00:00Z\"}", MediaType.APPLICATION_JSON));

        PaymentResponse response = client.get(id);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        server.verify();
    }

    @Test
    void listsWithFilters() {
        server.expect(requestTo("http://payments/api/payments?payerId=u1&status=COMPLETED"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.list("u1", PaymentStatus.COMPLETED)).isEmpty();
        server.verify();
    }

    @Test
    void throwsOnErrorResponse() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo("http://payments/api/payments/" + id + "/refund"))
                .andRespond(withStatus(HttpStatus.CONFLICT).body("{\"detail\":\"Cannot refund\"}"));

        assertThatThrownBy(() -> client.refund(id))
                .isInstanceOf(PaymentClientException.class)
                .extracting("statusCode").isEqualTo(409);
    }
}
