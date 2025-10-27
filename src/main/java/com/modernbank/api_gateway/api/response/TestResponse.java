package com.modernbank.api_gateway.api.response;

import lombok.*;

@Getter
@Setter
public class TestResponse {

    private String status = "";

    private String processCode = "";

    private String processMessage = "";
}