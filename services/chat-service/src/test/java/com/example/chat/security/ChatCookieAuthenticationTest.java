package com.example.chat.security;

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
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ChatCookieAuthenticationTest {
    private static final byte[] KEY = "test-chat-key-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8);
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "security.jwt.secret=" + Base64.getEncoder().encodeToString(KEY),
                "security.jwt.issuer=used-accounts", "security.jwt.audience=used-api",
                "security.auth-cookie.name=used_auth", "security.auth-cookie.secure=false",
                "security.cors.allowed-origins=http://localhost:8080");
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void close() { context.close(); }

    private String token(String subject, String role, String issuer, String audience, long expires, byte[] key) {
        return Jwts.builder().setSubject(subject).setIssuer(issuer).setAudience(audience)
                .claim("username", "alice").claim("roles", List.of(role))
                .setExpiration(new Date(expires)).signWith(Keys.hmacShaKeyFor(key)).compact();
    }

    private Cookie user() {
        return new Cookie("used_auth", token("12", "USER", "used-accounts", "used-api",
                System.currentTimeMillis() + 60_000, KEY));
    }

    @Test
    void verifiedSubjectDrivesCurrentActorRatherThanRequestUserId() throws Exception {
        var response = mvc.perform(get("/api/chat/conversations").cookie(user()).param("userId", "99"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("12:alice:USER", response.getContentAsString());
        assertNull(response.getCookie("JSESSIONID"));
        assertEquals(401, mvc.perform(get("/api/chat/conversations")).andReturn().getResponse().getStatus());
    }

    @Test
    void headersAndQueryCannotReplaceTheAuthCookie() throws Exception {
        assertEquals(401, mvc.perform(get("/api/chat/conversations")
                .header("Authorization", "Bearer " + user().getValue())
                .header("X-Internal-Token", "test-service-token-at-least-32-characters")
                .param("token", user().getValue())).andReturn().getResponse().getStatus());
    }

    @Test
    void invalidTokensAreRejected() throws Exception {
        long future = System.currentTimeMillis() + 60_000;
        List<String> tokens = List.of("broken",
                token("12", "USER", "wrong", "used-api", future, KEY),
                token("12", "USER", "used-accounts", "wrong", future, KEY),
                token("12", "USER", "used-accounts", "used-api", System.currentTimeMillis() - 120_000, KEY),
                token("12", "USER", "used-accounts", "used-api", future, new byte[32]),
                token("0", "USER", "used-accounts", "used-api", future, KEY),
                token("12", "INTERNAL", "used-accounts", "used-api", future, KEY));
        for (String token : tokens) {
            assertEquals(401, mvc.perform(get("/api/chat/conversations").cookie(new Cookie("used_auth", token)))
                    .andReturn().getResponse().getStatus());
        }
    }

    @Test
    void writesRequireMatchingCsrfCookieAndHeader() throws Exception {
        assertEquals(403, mvc.perform(post("/api/chat/conversations/7").cookie(user()))
                .andReturn().getResponse().getStatus());
        var bootstrap = mvc.perform(get("/api/chat/csrf-test").cookie(user()))
                .andReturn().getResponse();
        Cookie csrf = bootstrap.getCookie("used_csrf");
        assertNotNull(csrf);
        assertTrue(csrf.isHttpOnly());
        assertEquals(403, mvc.perform(post("/api/chat/conversations/7").cookie(user(), csrf)
                .header("X-XSRF-TOKEN", "wrong")).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(post("/api/chat/conversations/7").cookie(user(), csrf)
                .header("X-XSRF-TOKEN", bootstrap.getContentAsString())).andReturn().getResponse().getStatus());
    }

    @Test
    void sockJsTransportPathsSkipHttpCsrfButStillRequireAuthentication() throws Exception {
        assertEquals(401, mvc.perform(post("/ws/info")).andReturn().getResponse().getStatus());
        // No CSRF header is supplied: reaching the dispatcher (404) proves the transport path is exempt.
        assertEquals(404, mvc.perform(post("/ws/info").cookie(user()))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void corsAllowsOnlyConfiguredOriginWithCredentials() throws Exception {
        var allowed = mvc.perform(options("/api/chat/conversations/7").header("Origin", "http://localhost:8080")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-XSRF-TOKEN")).andReturn().getResponse();
        assertEquals(200, allowed.getStatus());
        assertEquals("true", allowed.getHeader("Access-Control-Allow-Credentials"));
        assertEquals("http://localhost:8080", allowed.getHeader("Access-Control-Allow-Origin"));
        assertEquals(403, mvc.perform(options("/api/chat/conversations/7").header("Origin", "https://other.example")
                .header("Access-Control-Request-Method", "POST")).andReturn().getResponse().getStatus());
    }

    @Test
    void healthIsPublic() throws Exception {
        assertEquals(200, mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfiguration.class, CurrentActorProvider.class, Probe.class})
    static class Config {}

    // Exercises the production security chain without the JPA repositories.
    @RestController
    static class Probe {
        private final CurrentActorProvider actors;
        Probe(CurrentActorProvider actors) { this.actors = actors; }
        @GetMapping("/api/chat/conversations")
        String mine(Principal principal) {
            CurrentActor actor = actors.get(principal);
            return actor.id() + ":" + actor.username() + ":" + actor.role();
        }
        @GetMapping("/api/chat/csrf-test")
        String csrf(CsrfToken token) { return token.getToken(); }
        @PostMapping("/api/chat/conversations/{id}")
        String open(Principal principal) { return actors.get(principal).id().toString(); }
        @GetMapping("/actuator/health")
        String health() { return "ok"; }
    }
}
