package com.modernbank.api_gateway.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(value = "authentication-service", url = "${client.feign.authentication-service.url}")
public interface AuthenticationServiceClient {

    @GetMapping("${client.feign.authentication-service.validate}")
    Boolean validateToken(@RequestParam("token") String token);
}
