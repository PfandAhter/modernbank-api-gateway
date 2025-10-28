# ModernBank API Gateway

ModernBank API Gateway is the single entry point to the ModernBank microservice landscape. It is built with Spring Boot 3, Spring Cloud Gateway, and Spring WebFlux to provide a fully reactive routing, security, and observability layer in front of downstream services such as authentication, account, transaction, notification, and MCP (Modern Communication Platform).

## Features

- **Reactive gateway** powered by Spring Cloud Gateway and WebFlux for non-blocking request handling. 【F:src/main/java/com/modernbank/api_gateway/ApiGatewayApplication.java†L1-L17】
- **Centralized authentication** that validates bearer tokens by calling the authentication service, attaches user metadata headers, and hydrates the reactive security context. 【F:src/main/java/com/modernbank/api_gateway/config/AuthenticationFilter.java†L25-L140】【F:src/main/java/com/modernbank/api_gateway/config/JwtAuthenticationManager.java†L1-L49】
- **Fine-grained authorization rules** that permit public endpoints while protecting the rest of the gateway surface. 【F:src/main/java/com/modernbank/api_gateway/config/SecurityConfiguration.java†L1-L47】
- **Redis-backed rate limiting** using Spring Cloud Gateway's `RequestRateLimiter` filter with a user-based key resolver. 【F:src/main/resources/application.yml†L46-L86】【F:src/main/java/com/modernbank/api_gateway/config/RateLimiterConfig.java†L1-L24】
- **Resilient error handling** that normalizes errors from downstream services into a consistent JSON structure. 【F:src/main/java/com/modernbank/api_gateway/config/GatewayErrorFilter.java†L1-L118】
- **Circuit breaker & fallback hooks** ready to be enabled to shield clients from upstream outages. 【F:src/main/resources/application.yml†L80-L84】【F:src/main/java/com/modernbank/api_gateway/controller/FallBackController.java†L1-L22】
- **Observability** via the Spring Boot actuator with Prometheus metrics support. 【F:pom.xml†L32-L79】【F:src/main/resources/application.yml†L9-L13】

## Project layout

```
.
├── pom.xml                     # Maven build configuration
├── src
│   ├── main
│   │   ├── java/com/modernbank/api_gateway
│   │   │   ├── ApiGatewayApplication.java
│   │   │   ├── api/             # DTOs and Feign clients for remote services
│   │   │   ├── config/          # Security, filters, rate limiting, error handling
│   │   │   └── controller/      # REST endpoints (fallback handlers)
│   │   └── resources
│   │       └── application.yml  # Routing, service discovery, redis, and CORS settings
│   └── test                     # Placeholder for unit / integration tests
└── mvnw, mvnw.cmd               # Maven wrapper scripts
```

## Prerequisites

- Java 17 or newer (matching the `java.version` property). 【F:pom.xml†L30-L31】
- Maven 3.9+ (or simply use the included Maven Wrapper scripts `./mvnw` / `mvnw.cmd`).
- A Redis instance reachable at `localhost:6379` for rate limiting (can be overridden via `spring.data.redis.*`). 【F:src/main/resources/application.yml†L24-L27】
- Downstream services listening on the default ports configured in `application.yml` (8081–8090) or update the URIs accordingly. 【F:src/main/resources/application.yml†L46-L79】

## Configuration

The gateway is configured via `src/main/resources/application.yml`.

Key sections include:

- `server.port`: The port exposed by the gateway (defaults to `8080`). 【F:src/main/resources/application.yml†L1-L8】
- `client.feign.authentication-service`: Base URL and validation endpoint for the Feign client used to validate tokens. 【F:src/main/resources/application.yml†L15-L19】
- `spring.cloud.gateway.routes`: Route definitions that map incoming paths to downstream services. Adjust `uri` values to match your environment. 【F:src/main/resources/application.yml†L46-L79】
- `spring.cloud.gateway.default-filters`: Global filters (response header deduplication, rate limiting). 【F:src/main/resources/application.yml†L80-L87】
- `spring.cloud.gateway.globalcors`: CORS settings for browser clients (default allows `http://localhost:3000`). 【F:src/main/resources/application.yml†L88-L93】

You can override any property using Spring Boot's standard mechanisms (environment variables, JVM system properties, or `--key=value` command-line flags). For example, to point the authentication service to another host: `--client.feign.authentication-service.url=http://auth:8081`.

## Running the gateway locally

1. Ensure Redis and the downstream services are running.
   - To launch Redis quickly with Docker: `docker run --rm -p 6379:6379 redis:7`.
2. From the repository root, start the gateway with the Maven Wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```
3. The gateway will be available at [http://localhost:8080](http://localhost:8080). Routes are proxied according to the `Path` predicates defined in `application.yml` (for example, `http://localhost:8080/transaction/**` will forward to the transaction service).

### Running as an executable JAR

To produce a runnable jar:
```bash
./mvnw clean package
java -jar target/api-gateway-0.0.1-SNAPSHOT.jar
```

## Testing

Execute the test suite with:
```bash
./mvnw test
```

Add unit or integration tests under `src/test/java` as the project evolves.

## Observability & health checks

- Health and info endpoints are exposed via Spring Boot Actuator at `/actuator/health` and `/actuator/info`. 【F:src/main/resources/application.yml†L9-L13】
- Prometheus metrics can be scraped when `management.metrics.export.prometheus.enabled=true` (add to `application.yml` or environment). The dependency is already included. 【F:pom.xml†L64-L67】

## Extending the gateway

- **Adding new routes:** Append another entry under `spring.cloud.gateway.routes` with your desired `id`, `uri`, predicates, and filters. 【F:src/main/resources/application.yml†L46-L79】
- **Custom filters:** Implement `GlobalFilter` or `GatewayFilterFactory` classes within `src/main/java/com/modernbank/api_gateway/config`.
- **Security adjustments:** Modify `SecurityConfiguration` to change permitted paths or authentication requirements. 【F:src/main/java/com/modernbank/api_gateway/config/SecurityConfiguration.java†L28-L40】
- **Error responses:** Tailor the shape of standardized errors in `GatewayErrorFilter`. 【F:src/main/java/com/modernbank/api_gateway/config/GatewayErrorFilter.java†L49-L103】
