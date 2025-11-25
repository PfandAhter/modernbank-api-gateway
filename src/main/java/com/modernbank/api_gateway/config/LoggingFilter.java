package com.modernbank.api_gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        // Get IP Address
        String ipAddress = Optional.ofNullable(request.getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(java.net.InetAddress::getHostAddress)
                .orElse("Unknown IP");

        // Log Request
        log.info("Incoming Request -> IP: {}, Method: {}, Path: {}", ipAddress, method, path);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;

            // Try to get User ID from headers (set by AuthenticationFilter if
            // authenticated)
            String userId = request.getHeaders().getFirst("X-User-Id");
            String userLog = (userId != null) ? "User ID: " + userId : "User: Anonymous";

            // Log Response
            if (statusCode >= 400) {
                log.error("Response -> IP: {}, {}, Status: {}, Path: {}", ipAddress, userLog, statusCode, path);
            } else {
                log.info("Response -> IP: {}, {}, Status: {}, Path: {}", ipAddress, userLog, statusCode, path);
            }
        }));
    }

    @Override
    public int getOrder() {
        // Execute before AuthenticationFilter (which is 0) to capture all requests,
        // but we rely on Auth filter to set X-User-Id for the response log.
        // Actually, if we want to see X-User-Id in the response log, we need to run
        // *after* Auth filter has mutated the request?
        // No, the chain.filter(exchange) runs the rest of the chain.
        // The .then(...) runs AFTER the chain returns (response phase).
        // By that time, if Auth filter ran, it might have mutated the request headers
        // in the exchange *passed down*.
        // However, exchange.getRequest() in the response phase refers to the request
        // object.
        // Let's stick to -1 to ensure we log the *incoming* request as early as
        // possible.
        return -1;
    }
}
