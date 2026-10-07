package com.example.Used.Security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfiguration {

    private MyUserDetailsService myUserDetailsService;


    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Autowired
    public void setMyUserDetailsService(MyUserDetailsService myUserDetailsService) {
        this.myUserDetailsService = myUserDetailsService;
    }

    @Bean
    public JWTRequestFilter authenticationJwtTokenFilter() {
        return new JWTRequestFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. STATIC RESOURCES (Always Public)
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()

                        // 2. SWAGGER (Always Public for Dev)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()

                        // 3. PUBLIC API ENDPOINTS (Auth Flows)
                        .requestMatchers(
                                "/auth/user/register",
                                "/auth/user/login",
                                "/auth/user/verify",
                                "/auth/user/resend-verify",
                                "/auth/user/forget-password",
                                "/auth/user/reset-password",
                                "/api/notifications/stream",
                                "/api/notifications/test-send/**",
                                "/api/categories",
                                "/api/listings/search",
                                "/api/listings/*"
                        ).permitAll()

                        // 4. PUBLIC HTML PAGES (Views)
                        // IMPORTANT: List these explicitly. Do not rely on wildcards for single pages unless necessary.
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/verify",
                                "/profile",
                                "/create-listing",
                                "/forgot-password",
                                "/listing/**",
                                "/css/**", "/js/**",
                                "/images/**",
                                "/favicon.ico", "/error"
                                ).permitAll()

                        // 5. ERROR PAGE (Must be public to avoid loops)
                        .requestMatchers("/error").permitAll()

                        // 6. SECURE EVERYTHING ELSE
                        .anyRequest().authenticated()
                );

        http.addFilterBefore(
                authenticationJwtTokenFilter(),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration
    )throws Exception{
        return authenticationConfiguration.getAuthenticationManager();
    }

}