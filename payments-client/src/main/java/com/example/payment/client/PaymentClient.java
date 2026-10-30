package com.example.payment.client;

import com.example.payment.client.dto.PaymentRequest;
import com.example.payment.client.dto.PaymentResponse;
import com.example.payment.client.model.PaymentStatus;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Java client for the payments-api REST endpoints. */
public class PaymentClient {

    private static final String BASE = "/api/payments";
    private static final String REGISTRATION_ID = "payments";

    /** One fixed principal so all calls share a single service token, regardless of the caller's login. */
    private static final Authentication SERVICE_PRINCIPAL = new AnonymousAuthenticationToken(
            "payments-client", "payments-client", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private final RestClient restClient;

    /** Creates a client for the given base URL, e.g. {@code http://localhost:8080}. */
    public PaymentClient(String baseUrl) {
        this(RestClient.builder().baseUrl(baseUrl));
    }

    /**
     * Creates a client that authenticates with OAuth2 client credentials. Access tokens are fetched
     * from {@code tokenUri} on first use, cached, and fetched again shortly before they expire.
     *
     * @param tokenUri e.g. {@code http://localhost:8180/realms/payments/protocol/openid-connect/token}
     * @param scopes   scopes to request, e.g. {@code payments.read}, {@code payments.write}
     */
    public static PaymentClient withClientCredentials(String baseUrl, String tokenUri,
                                                      String clientId, String clientSecret, String... scopes) {
        return new PaymentClient(RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(clientCredentialsInterceptor(tokenUri, clientId, clientSecret, scopes)));
    }

    /**
     * Interceptor that adds a client-credentials bearer token to each request. Use it when you build
     * your own {@link RestClient.Builder} (custom timeouts, etc.) and pass that builder to the constructor.
     */
    public static OAuth2ClientHttpRequestInterceptor clientCredentialsInterceptor(
            String tokenUri, String clientId, String clientSecret, String... scopes) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(tokenUri)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope(scopes)
                .build();
        var registrations = new InMemoryClientRegistrationRepository(registration);
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations, new InMemoryOAuth2AuthorizedClientService(registrations));
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build());

        var interceptor = new OAuth2ClientHttpRequestInterceptor(manager);
        interceptor.setClientRegistrationIdResolver(request -> REGISTRATION_ID);
        interceptor.setPrincipalResolver(request -> SERVICE_PRINCIPAL);
        return interceptor;
    }

    /** Creates a client from a pre-configured builder (custom timeouts, auth headers, etc.). */
    public PaymentClient(RestClient.Builder builder) {
        this.restClient = builder
                .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {
                    String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new PaymentClientException(res.getStatusCode().value(), body);
                })
                .build();
    }

    public PaymentResponse create(PaymentRequest request) {
        return restClient.post().uri(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PaymentResponse.class);
    }

    public PaymentResponse get(UUID id) {
        return restClient.get().uri(BASE + "/{id}", id).retrieve().body(PaymentResponse.class);
    }

    public List<PaymentResponse> list() {
        return list(null, null);
    }

    public List<PaymentResponse> list(String payerId, PaymentStatus status) {
        return restClient.get()
                .uri(b -> listUri(b, payerId, status))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public PaymentResponse process(UUID id) {
        return action(id, "process");
    }

    public PaymentResponse cancel(UUID id) {
        return action(id, "cancel");
    }

    public PaymentResponse refund(UUID id) {
        return action(id, "refund");
    }

    private PaymentResponse action(UUID id, String action) {
        return restClient.post().uri(BASE + "/{id}/" + action, id).retrieve().body(PaymentResponse.class);
    }

    private static URI listUri(UriBuilder b, String payerId, PaymentStatus status) {
        b.path(BASE);
        if (payerId != null) {
            b.queryParam("payerId", payerId);
        }
        if (status != null) {
            b.queryParam("status", status);
        }
        return b.build();
    }
}
