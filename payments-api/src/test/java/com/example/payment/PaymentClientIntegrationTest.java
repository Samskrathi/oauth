package com.example.payment;

import com.example.payment.client.PaymentClient;
import com.example.payment.client.PaymentClientException;
import com.example.payment.client.dto.PaymentRequest;
import com.example.payment.client.dto.PaymentResponse;
import com.example.payment.client.model.PaymentMethod;
import com.example.payment.client.model.PaymentStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Runs the real API and calls it through {@link PaymentClient} using the OAuth2 client-credentials flow.
 * A tiny local HTTP server stands in for Keycloak's token endpoint, and the API's {@link JwtDecoder}
 * is mocked to accept the token it hands out, so the test needs no running Keycloak.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentClientIntegrationTest {

    private static final String ACCESS_TOKEN = "test-access-token";

    private static HttpServer tokenServer;
    private static final List<String> tokenRequests = new CopyOnWriteArrayList<>();

    @LocalServerPort
    int port;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @BeforeAll
    static void startTokenServer() throws IOException {
        tokenServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        tokenServer.createContext("/token", exchange -> {
            tokenRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"access_token\":\"" + ACCESS_TOKEN + "\",\"token_type\":\"Bearer\","
                    + "\"expires_in\":300,\"scope\":\"payments.read payments.write\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        tokenServer.start();
    }

    @AfterAll
    static void stopTokenServer() {
        tokenServer.stop(0);
    }

    @BeforeEach
    void acceptTestToken() {
        tokenRequests.clear();
        given(jwtDecoder.decode(anyString())).willAnswer(invocation -> {
            String token = invocation.getArgument(0);
            if (!ACCESS_TOKEN.equals(token)) {
                throw new JwtException("invalid token");
            }
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject("payments-client")
                    .claim("scope", "payments.read payments.write")
                    .build();
        });
    }

    @Test
    void clientTalksToApiWithClientCredentials() {
        PaymentClient client = PaymentClient.withClientCredentials(
                "http://localhost:" + port,
                "http://localhost:" + tokenServer.getAddress().getPort() + "/token",
                "payments-client", "secret", "payments.read", "payments.write");

        PaymentResponse created = client.create(PaymentRequest.builder()
                .payerId("user-9").payeeId("shop-1")
                .amount(new BigDecimal("25.00")).currency("EUR")
                .method(PaymentMethod.UPI).build());
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.PENDING);

        PaymentResponse processed = client.process(created.getId());
        assertThat(processed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(processed.getTransactionReference()).startsWith("TXN-");

        assertThat(client.list("user-9", null)).hasSize(1);

        assertThatThrownBy(() -> client.cancel(created.getId()))
                .isInstanceOf(PaymentClientException.class)
                .extracting("statusCode").isEqualTo(409);

        // Token was fetched once with client credentials and reused for every call.
        assertThat(tokenRequests).hasSize(1);
        assertThat(tokenRequests.get(0)).contains("grant_type=client_credentials");
    }

    @Test
    void clientWithoutTokenGets401() {
        PaymentClient client = new PaymentClient("http://localhost:" + port);

        assertThatThrownBy(client::list)
                .isInstanceOf(PaymentClientException.class)
                .extracting("statusCode").isEqualTo(401);
    }
}
