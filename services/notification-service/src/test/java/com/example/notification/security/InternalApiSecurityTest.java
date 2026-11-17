package com.example.notification.security;

import com.example.notification.controller.InternalEmailController;
import com.example.notification.controller.InternalNotificationController;
import com.example.notification.service.EmailServices;
import com.example.notification.service.NotificationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class InternalApiSecurityTest {
    private static final String TOKEN = "test-internal-token-at-least-32-characters";
    private static final byte[] JWT_KEY = "test-jwt-key-with-32-bytes-minimum".getBytes(StandardCharsets.UTF_8);
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "security.jwt.secret=" + Base64.getEncoder().encodeToString(JWT_KEY),
                "security.jwt.issuer=used-accounts", "security.jwt.audience=used-api",
                "security.auth-cookie.name=used_auth", "security.auth-cookie.secure=false",
                "security.cors.allowed-origins=http://localhost:8080", "internal.api-token=" + TOKEN);
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    private Cookie userCookie() {
        String jwt = Jwts.builder().setSubject("12").setIssuer("used-accounts")
                .setAudience("used-api").claim("roles", List.of("USER"))
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(JWT_KEY)).compact();
        return new Cookie("used_auth", jwt);
    }

    @Test
    void pushUsesServiceTokenWithoutCookieOrCsrf() throws Exception {
        assertEquals(204, mvc.perform(post("/api/internal/notifications/push")
                .header("X-Internal-Token", TOKEN).contentType("application/json")
                .content("{\"userId\":12,\"eventName\":\"LISTING_SOLD\",\"data\":{\"listingId\":7}}"))
                .andReturn().getResponse().getStatus());
        verify(context.getBean(NotificationService.class))
                .sendToUser(12L, "LISTING_SOLD", Map.of("listingId", 7));
    }

    @Test
    void broadcastUsesServiceToken() throws Exception {
        assertEquals(204, mvc.perform(post("/api/internal/notifications/broadcast")
                .header("X-Internal-Token", TOKEN).contentType("application/json")
                .content("{\"eventName\":\"UPDATE\",\"data\":{\"message\":\"hello\"}}"))
                .andReturn().getResponse().getStatus());
        verify(context.getBean(NotificationService.class)).broadcast("UPDATE", Map.of("message", "hello"));
    }

    @Test
    void existingEmailEndpointUsesServiceToken() throws Exception {
        assertEquals(204, mvc.perform(post("/api/internal/email/listing-unavailable")
                .header("X-Internal-Token", TOKEN).contentType("application/json")
                .content("{\"to\":\"user@example.com\",\"listingTitle\":\"Bike\",\"reason\":\"sold\"}"))
                .andReturn().getResponse().getStatus());
        verify(context.getBean(EmailServices.class)).sendListingUnavailableEmail("user@example.com", "Bike", "sold");
    }

    @Test
    void missingWrongAndCookieOnlyCredentialsAreRejected() throws Exception {
        for (String path : List.of("/api/internal/notifications/push",
                "/api/internal/notifications/broadcast", "/api/internal/email/listing-unavailable")) {
            assertEquals(401, mvc.perform(post(path)).andReturn().getResponse().getStatus());
            assertEquals(401, mvc.perform(post(path).header("X-Internal-Token", "wrong"))
                    .andReturn().getResponse().getStatus());
            assertEquals(401, mvc.perform(post(path).cookie(userCookie()))
                    .andReturn().getResponse().getStatus());
        }
        verifyNoInteractions(context.getBean(NotificationService.class), context.getBean(EmailServices.class));
    }

    @Test
    void browserRoutesStillRequireJwtAndCsrf() throws Exception {
        assertEquals(401, mvc.perform(get("/api/notifications/test").header("X-Internal-Token", TOKEN))
                .andReturn().getResponse().getStatus());
        var bootstrap = mvc.perform(get("/api/notifications/test").cookie(userCookie()))
                .andReturn().getResponse();
        assertEquals(200, bootstrap.getStatus());
        assertEquals(403, mvc.perform(post("/api/notifications/test").cookie(userCookie()))
                .andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(post("/api/notifications/test")
                .cookie(userCookie(), bootstrap.getCookie("used_csrf"))
                .header("X-XSRF-TOKEN", bootstrap.getContentAsString()))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void shortInternalSecretIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new InternalTokenFilter("short"));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfiguration.class, InternalNotificationController.class, InternalEmailController.class,
            BrowserProbe.class})
    static class Config {
        @Bean NotificationService notificationService() { return mock(NotificationService.class); }
        @Bean EmailServices emailServices() { return mock(EmailServices.class); }
    }

    @RestController
    static class BrowserProbe {
        @GetMapping("/api/notifications/test")
        String csrf(CsrfToken token) { return token.getToken(); }
        @PostMapping("/api/notifications/test")
        String update() { return "ok"; }
    }
}
