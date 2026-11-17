package com.example.moderation.security;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ModerationCookieAuthenticationTest {
    private static final byte[] KEY = "test-moderation-key-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
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
                .claim("username", "USER".equals(role) ? "alice" : "admin")
                .claim("roles", List.of(role))
                .setExpiration(new Date(expires)).signWith(Keys.hmacShaKeyFor(key)).compact();
    }

    private Cookie cookie(String subject, String role) {
        return new Cookie("used_auth", token(subject, role, "used-accounts", "used-api",
                System.currentTimeMillis() + 60_000, KEY));
    }

    private Cookie user() {
        return cookie("12", "USER");
    }

    @Test
    void verifiedSubjectDrivesCurrentActorRatherThanRequestUserId() throws Exception {
        var response = mvc.perform(get("/api/reports/mine").cookie(user()).param("userId", "99"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("12:alice:USER", response.getContentAsString());
        assertNull(response.getCookie("JSESSIONID"));
        assertEquals(401, mvc.perform(get("/api/reports/mine")).andReturn().getResponse().getStatus());
    }

    @Test
    void headersAndQueryCannotReplaceTheAuthCookie() throws Exception {
        assertEquals(401, mvc.perform(get("/api/reports/mine")
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
            assertEquals(401, mvc.perform(get("/api/reports/mine").cookie(new Cookie("used_auth", token)))
                    .andReturn().getResponse().getStatus());
        }
    }

    @Test
    void onlyAdminsReachAdminReportRoutes() throws Exception {
        assertEquals(403, mvc.perform(get("/api/admin/reports/probe").cookie(user()))
                .andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(get("/api/admin/reports/probe").cookie(cookie("99", "ADMIN")))
                .andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/api/admin/reports/probe"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void writesRequireMatchingCsrfCookieAndHeader() throws Exception {
        assertEquals(403, mvc.perform(post("/api/reports").cookie(user()))
                .andReturn().getResponse().getStatus());
        var bootstrap = mvc.perform(get("/api/reports/csrf-test").cookie(user()))
                .andReturn().getResponse();
        Cookie csrf = bootstrap.getCookie("used_csrf");
        assertNotNull(csrf);
        assertTrue(csrf.isHttpOnly());
        assertEquals(403, mvc.perform(post("/api/reports").cookie(user(), csrf)
                .header("X-XSRF-TOKEN", "wrong")).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(post("/api/reports").cookie(user(), csrf)
                .header("X-XSRF-TOKEN", bootstrap.getContentAsString())).andReturn().getResponse().getStatus());
    }

    @Test
    void corsAllowsOnlyConfiguredOriginWithCredentials() throws Exception {
        var allowed = mvc.perform(options("/api/reports").header("Origin", "http://localhost:8080")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-XSRF-TOKEN")).andReturn().getResponse();
        assertEquals(200, allowed.getStatus());
        assertEquals("true", allowed.getHeader("Access-Control-Allow-Credentials"));
        assertEquals("http://localhost:8080", allowed.getHeader("Access-Control-Allow-Origin"));
        assertEquals(403, mvc.perform(options("/api/reports").header("Origin", "https://other.example")
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

        @GetMapping("/api/reports/mine")
        String mine() {
            CurrentActor actor = actors.get();
            return actor.id() + ":" + actor.username() + ":" + actor.role();
        }

        @GetMapping("/api/admin/reports/probe")
        String admin() { return actors.get().id().toString(); }

        @GetMapping("/api/reports/csrf-test")
        String csrf(CsrfToken token) { return token.getToken(); }

        @PostMapping("/api/reports")
        String submit() { return actors.get().id().toString(); }

        @GetMapping("/actuator/health")
        String health() { return "ok"; }
    }
}
