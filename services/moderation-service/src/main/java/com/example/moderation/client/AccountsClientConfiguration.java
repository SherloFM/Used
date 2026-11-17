package com.example.moderation.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AccountsClientConfiguration {
    @Bean
    public RestClient accountsRestClient(RestClient.Builder builder,
            @Value("${services.accounts.base-url}") String baseUrl,
            @Value("${internal.api-token}") String token) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(3_000);
        return builder.baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("X-Internal-Token", token).build();
    }
}
