package com.example.marketplace.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${security.auth-cookie.secure}") boolean secure) {
        var repository = new CookieCsrfTokenRepository();
        // Shared across services: every service compares this cookie with X-XSRF-TOKEN.
        repository.setCookieName("used_csrf");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie ->
                cookie.httpOnly(true).secure(secure).sameSite("Lax"));
        return repository;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${security.cors.allowed-origins}") String origins) {
        List<String> allowed = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (allowed.isEmpty() || allowed.contains("*")) {
            throw new IllegalArgumentException("Configure explicit frontend origins");
        }
        var config = new CorsConfiguration();
        config.setAllowedOrigins(allowed);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrfTokens,
            CorsConfigurationSource corsConfigurationSource,
            @Value("${security.jwt.secret}") String jwtSecret,
            @Value("${security.jwt.issuer}") String jwtIssuer,
            @Value("${security.jwt.audience}") String jwtAudience,
            @Value("${security.auth-cookie.name}") String cookieName,
            @Value("${internal.api-token}") String internalToken) throws Exception {

        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // The gateway has no browser session and is authenticated
                        // by its HMAC signature instead.
                        .ignoringRequestMatchers("/api/internal/**",
                                "/api/payments/gateway/callback"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Authentication required\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Access denied or invalid CSRF token\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/internal/**").hasRole("INTERNAL")
                        .requestMatchers(HttpMethod.POST, "/api/payments/gateway/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/listings/mine").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/listings", "/api/listings/search", "/api/listings/*",
                                "/api/categories", "/api/categories/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/wallet/admin/*/fund").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/listings/admin/*").hasRole("ADMIN")
                        .anyRequest().authenticated());

        http.addFilterBefore(
                new JwtCookieAuthenticationFilter(jwtSecret, jwtIssuer, jwtAudience, cookieName),
                UsernamePasswordAuthenticationFilter.class);

        http.addFilterBefore(new InternalTokenFilter(internalToken), JwtCookieAuthenticationFilter.class);
        return http.build();
    }
}
