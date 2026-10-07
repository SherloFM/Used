package com.example.Used.Config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI usedMarketplaceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("USED Marketplace API")
                        .description("API documentation for the USED items marketplace")
                        .version("1.0.0"));
    }
}
