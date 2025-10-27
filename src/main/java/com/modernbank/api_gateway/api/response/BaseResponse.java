package com.modernbank.api_gateway.api.response;


import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaseResponse {

    private String status = "OK";

    private String processCode = "H-0001";

    private String processMessage = "SUCCESS";

    public BaseResponse(String processMessage){
        this.processCode = "H-0001";
        this.processMessage = processMessage;
    }
}