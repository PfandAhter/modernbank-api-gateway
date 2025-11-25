package com.modernbank.api_gateway.config;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class SecurityContextRepository implements ServerSecurityContextRepository {

    private final JwtAuthenticationManager authenticationManager;

    public SecurityContextRepository(JwtAuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        return Mono.empty(); // stateless
    }

    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        //Burasini kaldirabilirmisim bir onemi yokmus.
        if (exchange.getRequest().getPath().toString().startsWith("/authentication") ||
                exchange.getRequest().getPath().toString().startsWith("/api/v1/verification/user") ||
                exchange.getRequest().getPath().toString().startsWith("/account/api/v1/verification/user")||
                exchange.getRequest().getPath().toString().startsWith("/notification/notification-websocket")||
                exchange.getRequest().getPath().toString().startsWith("/notification-websocket")||
                exchange.getRequest().getPath().toString().startsWith("/notification/chat-websocket")||
                exchange.getRequest().getPath().toString().startsWith("/chat-websocket")
        ) {
            return Mono.empty();
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Authentication auth = new UsernamePasswordAuthenticationToken(token, token);
            return authenticationManager.authenticate(auth).map(org.springframework.security.core.context.SecurityContextImpl::new);
        }
        return Mono.empty();
    }
}