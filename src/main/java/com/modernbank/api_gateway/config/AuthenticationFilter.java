package com.modernbank.api_gateway.config;

import com.modernbank.api_gateway.api.response.UserInfoResponse;
import com.modernbank.api_gateway.exception.RemoteServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final WebClient.Builder webClientBuilder;

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

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        // AuthenticationService’e doğrulama isteği gönder
        return webClientBuilder.build()
                .get()
                .uri("http://localhost:8081/authentication/validate?token=" + token)
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
                    // Header’a kullanıcı bilgilerini ekle
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

    private Mono<UserInfoResponse> validateToken(String token) {
        return webClientBuilder.build()
                .get()
                .uri("http://localhost:8081/authentication/validate?token=" + token)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .bodyToMono(UserInfoResponse.class);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}