# Payments

Gradle multi-module project — Spring Boot 3.5, Lombok, Java 17.

| Module | What it is |
|---|---|
| `payments-client` | Plain Java library: shared DTOs (`PaymentRequest`, `PaymentResponse`), enums, and `PaymentClient` for calling the API |
| `payments-api` | Spring Boot REST service (JPA + H2). Depends on `payments-client` for its request/response types |

## Build & run
```bash
docker compose up -d                 # start Keycloak on http://localhost:8180 (admin / admin)
./gradlew build                      # build + test both modules (tests don't need Keycloak)
./gradlew :payments-api:bootRun      # start the API on http://localhost:8080
./gradlew :payments-client:jar       # build the client library jar
```

## Security (OAuth2 + Spring Security)
The API is an OAuth2 **resource server**: every request needs a JWT access token issued by Keycloak
(realm `payments`, imported from `keycloak/payments-realm.json`). Issuer URL is set in `application.yml`
and can be overridden with the `KEYCLOAK_ISSUER_URI` environment variable.

| Scope | Allows |
|---|---|
| `payments.read` | `GET /api/payments/**` |
| `payments.write` | `POST /api/payments/**` (create, process, cancel, refund) |

`/actuator/health` and `/actuator/info` are public. No token → `401`, wrong scope → `403`.

Test clients (client-credentials grant):

| Client ID | Secret | Scopes |
|---|---|---|
| `payments-client` | `payments-client-secret` | read + write |
| `payments-reader` | `payments-reader-secret` | read only |

Try it with curl:
```bash
TOKEN=$(curl -s http://localhost:8180/realms/payments/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=payments-client -d client_secret=payments-client-secret \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')

curl -s -X POST localhost:8080/api/payments -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"payerId":"user-1","payeeId":"merchant-1","amount":49.99,"currency":"USD","method":"CREDIT_CARD"}'
```

## API
| Method | Path | Description |
|---|---|---|
| POST | `/api/payments` | Create a payment (status `PENDING`) |
| GET | `/api/payments/{id}` | Get one payment |
| GET | `/api/payments?payerId=&status=` | List payments, optional filters |
| POST | `/api/payments/{id}/process` | `PENDING` → `COMPLETED` |
| POST | `/api/payments/{id}/cancel` | `PENDING` → `CANCELLED` |
| POST | `/api/payments/{id}/refund` | `COMPLETED` → `REFUNDED` |

## Using the client
```groovy
dependencies { implementation project(':payments-client') }
```
```java
PaymentClient client = PaymentClient.withClientCredentials(
        "http://localhost:8080",
        "http://localhost:8180/realms/payments/protocol/openid-connect/token",
        "payments-client", "payments-client-secret");   // tokens are fetched, cached and renewed automatically

PaymentResponse p = client.create(PaymentRequest.builder()
        .payerId("user-1").payeeId("merchant-1")
        .amount(new BigDecimal("49.99")).currency("USD")
        .method(PaymentMethod.CREDIT_CARD).build());

client.process(p.getId());
```
Error responses (4xx/5xx) throw `PaymentClientException` with `getStatusCode()` and `getResponseBody()`.
For timeouts etc., pass your own `RestClient.Builder` to the constructor and add
`PaymentClient.clientCredentialsInterceptor(tokenUri, clientId, secret)` as a request interceptor.
Token-endpoint failures (e.g. wrong secret) throw Spring Security's `ClientAuthorizationException`.
