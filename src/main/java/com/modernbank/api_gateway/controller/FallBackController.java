package com.modernbank.api_gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class FallBackController {

    @RequestMapping("/fallback/authentication")
    public ResponseEntity<Map<String, String>> authenticationFallback() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "FAILED");
        response.put("processCode", "ERR-503");
        response.put("processMessage", "Authentication service temporarily unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}
