package com.modernbank.api_gateway.config;

import com.modernbank.api_gateway.api.client.AuthenticationServiceClient;
import com.modernbank.api_gateway.api.response.UserInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final WebClient.Builder webClientBuilder;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = authentication.getCredentials().toString();

        return webClientBuilder.build()
                .get()
                .uri("http://localhost:8081/authentication/validate?token=" + token)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> Mono.empty()) // hata gelirse auth başarısız
                .bodyToMono(UserInfoResponse.class)
                .map(userInfo -> {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userInfo.getEmail(), null,
                            userInfo.getAuthorities().stream()
                                    .map(SimpleGrantedAuthority::new)
                                    .toList()
                    );
                    return auth;
                });
    }
}
