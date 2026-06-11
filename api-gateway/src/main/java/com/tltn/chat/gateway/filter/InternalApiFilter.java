package com.tltn.chat.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class InternalApiFilter extends AbstractGatewayFilterFactory<InternalApiFilter.Config> {

    @Value("${app.internal-secret}")
    private String internalSecret;

    public InternalApiFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String internalToken = request.getHeaders().getFirst("X-Internal-Token");

            if (internalToken == null || !internalToken.equals(internalSecret)) {
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return response.setComplete();
            }

            return chain.filter(exchange);
        };
    }

    public static class Config {
        // Có thể thêm các cấu hình tuỳ chỉnh nếu cần thiết
    }
}
