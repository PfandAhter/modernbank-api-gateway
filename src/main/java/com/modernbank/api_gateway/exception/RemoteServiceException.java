package com.modernbank.api_gateway.exception;

import org.springframework.http.HttpStatus;

public class RemoteServiceException extends RuntimeException {
    private final HttpStatus status;
    private final String processCode;
    private final String processMessage;

    public RemoteServiceException(HttpStatus status, String processCode, String processMessage) {
        super(processMessage);
        this.status = status;
        this.processCode = processCode;
        this.processMessage = processMessage;
    }

    public String getErrorCode() {
        return processCode;
    }

    public String getErrorMessage() {
        return processMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }
}