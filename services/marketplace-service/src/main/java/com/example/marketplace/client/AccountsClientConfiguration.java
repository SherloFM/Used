package com.example.marketplace.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AccountsClientConfiguration {

    /**
     * One shared client for Accounts internal calls. Timeouts are short on
     * purpose: a lookup on a page-render path must fail fast, not hang.
     */
    @Bean
    public RestClient accountsRestClient(RestClient.Builder builder,
            @Value("${services.accounts.base-url}") String baseUrl,
            @Value("${internal.api-token}") String internalToken) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(3_000);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }
}
