package com.modernbank.api_gateway.config;

import com.modernbank.api_gateway.api.response.UserInfoResponse;
import com.modernbank.api_gateway.constants.HeaderKey;
import com.modernbank.api_gateway.exception.RemoteServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.modernbank.api_gateway.constants.HeaderKey.*;


@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final WebClient.Builder webClientBuilder;

    @Value("${client.feign.authentication-service.url}")
    private String authServiceUrl;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }

        String notificationPath = exchange.getRequest().getURI().getPath();
        if (notificationPath.startsWith("/notification/notification-websocket") || notificationPath.startsWith("/notification/chat-websocket")) {
            return chain.filter(exchange);
        }

        // WebSocket upgrade kontrolü
        if ("websocket".equalsIgnoreCase(request.getHeaders().getUpgrade())) {
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return chain.filter(exchange);
            }

            String token = authHeader.substring(7);
            return validateToken(token).flatMap(userInfo -> {
                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                        .header("X-User-Id", userInfo.getId())
                        .header("X-User-Email", userInfo.getEmail())
                        .header("X-User-Role", String.join(",", userInfo.getAuthorities()))
                        .build();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userInfo.getEmail(), null,
                                userInfo.getAuthorities().stream()
                                        .map(SimpleGrantedAuthority::new)
                                        .toList()
                        );

                SecurityContext context = new SecurityContextImpl(authentication);

                return chain.filter(exchange.mutate().request(mutatedRequest).build())
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
            });
        }

        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/authentication") ||
                path.startsWith("/api/v1/verification/user") ||
                path.startsWith("/account/api/v1/verification/user")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID) != null ?
                exchange.getRequest().getHeaders().getFirst(CORRELATION_ID) :
                UUID.randomUUID().toString();

        if(correlationId == null || correlationId.isEmpty()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        String validateUrl = authServiceUrl + "/authentication/validate?token=" + token;
        // AuthenticationService’e doğrulama isteği gönder
        String finalCorrelationId = correlationId;
        return webClientBuilder.build()
                .get()
                .uri(validateUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp ->
                        resp.bodyToMono(String.class).flatMap(body ->
                                Mono.error(new RemoteServiceException(
                                        HttpStatus.UNAUTHORIZED,
                                        "BAD_CREDENTIALS_PROVIDED",
                                        "Kullanıcı doğrulaması başarısız: " + body
                                ))
                        )
                )
                .onStatus(HttpStatusCode::is5xxServerError, resp ->
                        resp.bodyToMono(String.class).flatMap(body ->
                                Mono.error(new RemoteServiceException(
                                        HttpStatus.BAD_GATEWAY,
                                        "AUTH_SERVICE_UNAVAILABLE",
                                        "Kimlik doğrulama servisine ulaşılamıyor."
                                ))
                        )
                )
                .bodyToMono(UserInfoResponse.class)
                .flatMap(userInfo -> {
                    String pathAdminAccess = exchange.getRequest().getURI().getPath();

                    if (requiresAdminAccess(pathAdminAccess)) {
                        boolean isAdmin = userInfo.getAuthorities().stream()
                                .anyMatch(auth -> auth.equalsIgnoreCase("ROLE_ADMIN") || auth.equalsIgnoreCase("ADMIN"));

                        if (!isAdmin) {
                            return handleUnauthorizedAdminAccess(exchange);
                        }
                    }
                    // Header’a kullanıcı bilgilerini ekle
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .header(USER_ID, userInfo.getId())
                            .header(USER_EMAIL, userInfo.getEmail())
                            .header(USER_ROLE, String.join(",", userInfo.getAuthorities()))
                            .header(CORRELATION_ID, finalCorrelationId)
                            .build();

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userInfo.getEmail(), null,
                                    userInfo.getAuthorities().stream()
                                            .map(SimpleGrantedAuthority::new)
                                            .toList()
                            );

                    SecurityContext context = new SecurityContextImpl(authentication);

                    return chain.filter(exchange.mutate().request(mutatedRequest).build())
                            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
                }).onErrorResume(e -> {
                    if (e instanceof RemoteServiceException rse) {
                        return Mono.error(rse);
                    }

                    if (e instanceof java.net.ConnectException) {
                        return Mono.error(new RemoteServiceException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "SUNUCU HATASI",
                                "İlgili servis şu anda yanıt vermiyor veya erişilemiyor."
                        ));
                    }

                    return Mono.error(new RemoteServiceException(
                            HttpStatus.NOT_ACCEPTABLE,
                            "AUTH_SERVICE_ERROR",
                            "Kimlik doğrulama işlemi sırasında hata oluştu: " + e.getMessage()
                    ));
                });
    }

    private boolean requiresAdminAccess(String path) {
        return path.contains("/cache") || path.contains("/admin");
    }

    private Mono<Void> handleUnauthorizedAdminAccess(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.NOT_FOUND);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = """
        {
            "description": "Talep edilen kaynak sistemde bulunamadı veya bu işlem için gerekli izinler sağlanamadı.",
            "error": "Erişim Kısıtlaması",
            "status": 404
        }
        """;

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<UserInfoResponse> validateToken(String token) {
        String validateUrl = authServiceUrl + "/authentication/validate?token=" + token;
        return webClientBuilder.build()
                .get()
                .uri(validateUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(UserInfoResponse.class);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}