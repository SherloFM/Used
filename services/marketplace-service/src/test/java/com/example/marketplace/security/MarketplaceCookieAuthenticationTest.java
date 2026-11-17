package com.example.marketplace.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class MarketplaceCookieAuthenticationTest {

    private static final String SECRET;
    static {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        SECRET = Base64.getEncoder().encodeToString(bytes);
    }

    private static final String ISSUER = "used-accounts";
    private static final String AUDIENCE = "used-api";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "security.jwt.secret=" + SECRET,
                "internal.api-token=test-internal-token-at-least-32-characters",
                "security.jwt.issuer=" + ISSUER,
                "security.jwt.audience=" + AUDIENCE,
                "security.auth-cookie.name=used_auth",
                "security.auth-cookie.secure=false",
                "security.cors.allowed-origins=http://localhost:8080");
        context.register(TestConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void validCookieAuthenticatesBySubjectId() throws Exception {
        var response = mvc.perform(get("/api/listings/mine")
                .cookie(new Cookie("used_auth", token("12", List.of("USER")))))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        assertEquals("12", response.getContentAsString());
    }

    @Test
    void bearerHeaderAndQueryParameterAreNotAuthentication() throws Exception {
        String token = token("12", List.of("USER"));

        assertEquals(401, mvc.perform(get("/api/listings/mine")
                        .param("token", token)
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void publicCatalogueStaysOpen() throws Exception {
        assertEquals(200, mvc.perform(get("/api/categories"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void adminDeleteRequiresAdminRole() throws Exception {
        var bootstrap = mvc.perform(get("/api/categories")).andReturn().getResponse();
        Cookie csrfCookie = bootstrap.getCookie("used_csrf");
        String csrfToken = bootstrap.getContentAsString();

        Cookie user = new Cookie("used_auth", token("12", List.of("USER")));
        Cookie admin = new Cookie("used_auth", token("13", List.of("ADMIN")));

        assertEquals(403, mvc.perform(delete("/api/listings/admin/5")
                        .cookie(user, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andReturn().getResponse().getStatus());

        assertEquals(200, mvc.perform(delete("/api/listings/admin/5")
                        .cookie(admin, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void csrfIsRequiredForUnsafeMethods() throws Exception {
        Cookie auth = new Cookie("used_auth", token("12", List.of("USER")));

        assertEquals(403, mvc.perform(post("/api/profile").cookie(auth))
                .andReturn().getResponse().getStatus());

        var bootstrap = mvc.perform(get("/api/categories")).andReturn().getResponse();
        Cookie csrfCookie = bootstrap.getCookie("used_csrf");
        String csrfToken = bootstrap.getContentAsString();
        org.junit.jupiter.api.Assertions.assertNotNull(csrfCookie);

        assertEquals(200, mvc.perform(post("/api/profile")
                        .cookie(auth, csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken))
                .andReturn().getResponse().getStatus());

        assertEquals(403, mvc.perform(post("/api/profile")
                        .cookie(auth, csrfCookie)
                        .header("X-XSRF-TOKEN", "wrong"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void onlyConfiguredFrontendOriginReceivesCredentialedCors() throws Exception {
        var allowed = mvc.perform(options("/api/profile")
                        .header("Origin", "http://localhost:8080")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andReturn().getResponse();

        assertEquals(200, allowed.getStatus());
        assertEquals("true", allowed.getHeader("Access-Control-Allow-Credentials"));

        assertEquals(403, mvc.perform(options("/api/profile")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void expiredWrongIssuerWrongAudienceAndBadClaimsAreRejected() throws Exception {
        long now = System.currentTimeMillis();

        assertRejected(tokenBuilder("12", List.of("USER"))
                .setIssuedAt(new Date(now - 120_000))
                .setExpiration(new Date(now - 60_000)));
        assertRejected(tokenBuilder("12", List.of("USER")).setIssuer("other"));
        assertRejected(tokenBuilder("12", List.of("USER")).setAudience("other"));
        assertRejected(tokenBuilder("abc", List.of("USER")));
        assertRejected(tokenBuilder("12", List.of("SUPER")));
        assertRejected(tokenBuilder("12", List.of()));

        String foreign = Jwts.builder()
                .setSubject("12")
                .setIssuer(ISSUER)
                .setAudience(AUDIENCE)
                .setIssuedAt(new Date())
                .setExpiration(new Date(now + 60_000))
                .claim("roles", List.of("USER"))
                .signWith(Keys.hmacShaKeyFor(new byte[32]), SignatureAlgorithm.HS256)
                .compact();
        assertEquals(401, mvc.perform(get("/api/profile").cookie(new Cookie("used_auth", foreign)))
                .andReturn().getResponse().getStatus());
    }

    private void assertRejected(io.jsonwebtoken.JwtBuilder builder) throws Exception {
        String token = builder.signWith(KEY, SignatureAlgorithm.HS256).compact();
        assertEquals(401, mvc.perform(get("/api/profile").cookie(new Cookie("used_auth", token)))
                .andReturn().getResponse().getStatus());
    }

    private String token(String subject, List<String> roles) {
        return tokenBuilder(subject, roles).signWith(KEY, SignatureAlgorithm.HS256).compact();
    }

    private io.jsonwebtoken.JwtBuilder tokenBuilder(String subject, List<String> roles) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuer(ISSUER)
                .setAudience(AUDIENCE)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 60_000))
                .claim("roles", roles)
                .claim("username", "tester");
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfiguration.class)
    static class TestConfiguration {
        @Bean
        ProbeController probe() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/categories")
        String categories(CsrfToken token) {
            return token.getToken();
        }

        @GetMapping("/api/listings/mine")
        String mine(@AuthenticationPrincipal CurrentActor actor) {
            return actor.id().toString();
        }

        @GetMapping("/api/profile")
        String profile(@AuthenticationPrincipal CurrentActor actor) {
            return actor.id().toString();
        }

        @PostMapping("/api/profile")
        String update() {
            return "updated";
        }

        @DeleteMapping("/api/listings/admin/{id}")
        String adminDelete(@PathVariable Long id) {
            return "deleted " + id;
        }
    }
}
