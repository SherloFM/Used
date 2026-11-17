package com.example.notification.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AccountsClientConfiguration {

    /**
     * One shared client for Accounts internal calls. Timeouts are short on
     * purpose: a notification must fail fast rather than hold the listener
     * thread, because a failure is retried and then dead-lettered.
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
