package com.modernbank.api_gateway.config;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.List;


//@Configuration
public class GlobalCorsConfig {

    /*@Bean
    public CorsWebFilter corsWebFilter() {
        // 1. Create a source for CORS configurations
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());

        // 2. Define the CORS configuration for all paths (/**)
        CorsConfiguration corsConfig = new CorsConfiguration();

        // 3. Configure the settings
        //corsConfig.setAllowedOrigins(List.of("http://localhost:3000")); // Your frontend origin
        corsConfig.setAllowedOriginPatterns(List.of("http://localhost:3000")); // Allow specific origin pattern
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")); // Allow all methods (GET, POST, PUT, DELETE, OPTIONS, etc.)
        corsConfig.addAllowedHeader("*"); // Allow all headers
        corsConfig.setAllowCredentials(true); // Allow sending credentials (like cookies or auth headers)
        corsConfig.setMaxAge(3600L); // How long the preflight response can be cached (in seconds)

        // 4. Register the configuration for all paths
        source.registerCorsConfiguration("/**", corsConfig);

        // 5. Create and return the filter
        return new CorsWebFilter(source);
    }

    /*@Bean
    public GlobalFilter corsPreflightFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            if (request.getMethod() == HttpMethod.OPTIONS) {
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.OK);
                response.getHeaders().set("Access-Control-Allow-Origin", "http://localhost:3000");
                response.getHeaders().set("Access-Control-Allow-Credentials", "true");
                response.getHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
                response.getHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                return response.setComplete(); // burada backend'e hiç gitmez
            }
            return chain.filter(exchange);
        };
    }*/

    /*@Bean
    @Order(-1)
    public GlobalFilter websocketCorsHeaderInjector() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            String path = request.getURI().getPath();

            if (path.contains("/notification-websocket/info")) {
                HttpHeaders headers = response.getHeaders();
                // Don't set Access-Control-Allow-Origin here as it's handled by CorsWebFilter
                if (!headers.containsKey("Access-Control-Allow-Credentials")) {
                    headers.set("Access-Control-Allow-Credentials", "true");
                }
                headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
                headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            }

            return chain.filter(exchange);
        };
    }*/
}