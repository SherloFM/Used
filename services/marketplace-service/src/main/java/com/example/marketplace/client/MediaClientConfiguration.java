package com.example.marketplace.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class MediaClientConfiguration {

    @Bean
    public RestClient mediaRestClient(RestClient.Builder builder,
            @Value("${services.media.base-url}") String baseUrl,
            @Value("${internal.api-token}") String internalToken) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        // Uploads may be up to 5 MB, so allow more time than a metadata lookup.
        factory.setReadTimeout(15_000);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }
}
