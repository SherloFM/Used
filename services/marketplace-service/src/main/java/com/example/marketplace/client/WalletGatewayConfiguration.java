package com.example.marketplace.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The gateway is an external HTTPS service, so the timeouts are deliberately
 * shorter than a browser request: a payment page that waits for it must fail
 * with a clear message instead of hanging.
 */
@Configuration
public class WalletGatewayConfiguration {

    @Bean
    public RestClient walletGatewayRestClient(RestClient.Builder builder,
            @Value("${wallet.gateway.base-url}") String baseUrl,
            @Value("${wallet.gateway.api-key:}") String apiKey) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(10_000);

        RestClient.Builder configured = builder
                .baseUrl(baseUrl)
                .requestFactory(factory);

        if (apiKey != null && !apiKey.isBlank()) {
            // Only send the header when a key exists; the client reports the
            // "not configured" case itself with a user-facing message.
            configured.defaultHeader("X-API-Key", apiKey.trim());
        }
        return configured.build();
    }
}
