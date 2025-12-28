package com.modernbank.api_gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernbank.api_gateway.api.response.BaseResponse;
import com.modernbank.api_gateway.api.response.TestResponse;
import com.modernbank.api_gateway.exception.RemoteServiceException;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.WriteTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.AnnotatedException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;


@Slf4j
@Component
public class GatewayErrorFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(throwable -> handleException(exchange, throwable));
    }

    private Mono<Void> handleException(ServerWebExchange exchange, Throwable throwable) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String processCode = "ERR-UNEXPECTED";
        String processMessage = "Beklenmeyen bir hata oluştu. Lütfen daha sonra tekrar deneyiniz.";

        try {
            // 🔹 Yetkilendirme hatası (ör. Token geçersiz)

            if (isTimeoutException(throwable)) {
                status = HttpStatus.GATEWAY_TIMEOUT;
                processCode = "ERR-TIMEOUT";
                processMessage = "İstek zaman aşımına uğradı. Sunucu yanıt vermedi, lütfen daha sonra tekrar deneyiniz.";
                log.warn("Gateway Timeout [path={}]: {}", exchange.getRequest().getPath(), throwable.getMessage());
            }

            else if (throwable instanceof RemoteServiceException rse) {
                status = rse.getStatus() != null ? rse.getStatus() : HttpStatus.UNAUTHORIZED;
                processCode = rse.getErrorCode() != null ? rse.getErrorCode() : "AUTH-001";
                processMessage = rse.getMessage() != null ? rse.getMessage() : "Yetkilendirme hatası.";
            }

            // 🔹 Mikroservise erişilemiyor (ör. servis down)
            else if (throwable instanceof WebClientRequestException) {
                status = HttpStatus.BAD_GATEWAY;
                processCode = "ERR-UPSTREAM";
                processMessage = "Bağlantı sağlanamıyor. Lütfen daha sonra tekrar deneyiniz.";
            }

            // 🔹 Spesifik durumlar (ör. 404, 403)
            else if (throwable instanceof ResponseStatusException rse) {
                status = (HttpStatus) rse.getStatusCode();
                processCode = "ERR-" + status.value();
                processMessage = rse.getReason() != null ? rse.getReason() : mapStatusToMessage(status);
            }

            // 🔹 NullPointer, IllegalState vb. framework hataları
            else {
                log.error("Gateway Error [path={}]: {}", exchange.getRequest().getPath(), throwable.toString(), throwable);
                processCode = mapStatusToProcessCode(status);
                processMessage = mapStatusToMessage(status);
            }

        } catch (Exception e) {
            log.error("Error while handling exception: {}", e.getMessage(), e);
        }

        response.setStatusCode(status);

        // 🔹 BaseResponse kullanarak tek tip yanıt oluştur
        BaseResponse baseResponse = BaseResponse.builder()
                .status("FAILED")
                .processCode(processCode)
                .processMessage(processMessage)

                .build();

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(baseResponse);
        } catch (JsonProcessingException e) {
            bytes = "{\"status\":\"FAILED\",\"processCode\":\"ERR-SERIALIZE\",\"processMessage\":\"Yanıt serileştirilirken hata oluştu.\"}"
                    .getBytes(StandardCharsets.UTF_8);
        }

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private boolean isTimeoutException(Throwable throwable) {
        return throwable instanceof TimeoutException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof ReadTimeoutException
                || throwable instanceof WriteTimeoutException
                || (throwable.getCause() != null && isTimeoutException(throwable.getCause()))
                || (throwable.getMessage() != null && throwable.getMessage().toLowerCase().contains("timeout"));
    }

    private String mapStatusToProcessCode(HttpStatus status) {
        if (status.is4xxClientError()) return "ERR-CLIENT";
        if (status.is5xxServerError()) return "ERR-SERVER";
        return "ERR-UNKNOWN";
    }

    private String mapStatusToMessage(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "Yetkilendirme hatası.";
            case FORBIDDEN -> "Erişim reddedildi.";
            case NOT_FOUND -> "İstenen kaynak bulunamadı.";
            default -> status.is5xxServerError()
                    ? "Sunucu hatası, lütfen daha sonra tekrar deneyiniz."
                    : "Bir hata oluştu.";
        };
    }

    @Override
    public int getOrder() {
        return -2; // Daha erken yakalaması için
    }
}

/*@Component
public class GatewayErrorFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        return chain.filter(exchange)
                .onErrorResume(throwable -> {

                    ServerHttpResponse response = exchange.getResponse();
                    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

                    HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
                    String processCode = "ERR-UNEXPECTED";
                    String processMessage = "Beklenmeyen bir hata oluştu. Lütfen daha sonra tekrar deneyiniz.";

                    if (throwable instanceof RemoteServiceException rse) {
                        status = HttpStatus.UNAUTHORIZED; // sabit veya exception'dan alabilirsin
                        processCode = rse.getErrorCode() != null ? rse.getErrorCode() : "AUTH-001";
                        processMessage = rse.getMessage() != null ? rse.getMessage() : "Yetkilendirme hatası.";
                    }

                    // 🔹 Ulaşılmayan upstream servis (örneğin mikroservis kapalıysa)
                    else if (throwable instanceof WebClientRequestException) {
                        status = HttpStatus.BAD_GATEWAY;
                        processCode = "ERR-UPSTREAM";
                        processMessage = "Bağlantı sağlanamıyor. Lütfen tekrar deneyiniz.";
                    }

                    // 🔹 Spring'in ResponseStatusException'ı (örnek: 404, 403)
                    else if (throwable instanceof ResponseStatusException rse) {
                        status = (HttpStatus) rse.getStatusCode();
                        processCode = "ERR-" + status.value();
                        processMessage = rse.getReason() != null ? rse.getReason() : status.getReasonPhrase();
                    }

                    response.setStatusCode(status);

                    // 🔹 Tüm hata cevaplarını tek tip BaseResponse ile dön
                    BaseResponse baseResponse = BaseResponse.builder()
                            .status("FAILED")
                            .processCode(processCode)
                            .processMessage(processMessage)
                            .build();

                    byte[] bytes;
                    try {
                        bytes = objectMapper.writeValueAsBytes(baseResponse);
                    } catch (JsonProcessingException e) {
                        bytes = "{\"success\":false,\"message\":\"Error serializing response.\"}"
                                .getBytes(StandardCharsets.UTF_8);
                    }

                    DataBuffer buffer = response.bufferFactory().wrap(bytes);
                    return response.writeWith(Mono.just(buffer));
                });
    }

    private boolean isJson(String body) {
        if (body == null || body.isBlank()) return false;
        String trimmed = body.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private String mapStatusToProcessCode(HttpStatus status) {
        if (status.is4xxClientError()) return "ERR-CLIENT";
        if (status.is5xxServerError()) return "ERR-SERVER";
        return "ERR-UNKNOWN";
    }

    private String mapStatusToMessage(HttpStatus status) {
        if (status == HttpStatus.UNAUTHORIZED) return "Yetkilendirme hatası.";
        if (status == HttpStatus.FORBIDDEN) return "Erişim reddedildi.";
        if (status.is4xxClientError()) return "İstemci hatası.";
        if (status.is5xxServerError()) return "Sunucu hatası, lütfen daha sonra tekrar deneyiniz.";
        return "Hata oluştu.";
    }

    @Override
    public int getOrder() {
        return -1; //was -2 daha önce yakalanmak isteniyorsa -1 veya daha düşük verebilirsin
    }
}*/