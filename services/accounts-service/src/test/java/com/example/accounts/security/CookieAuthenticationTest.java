package com.example.accounts.security;

import com.example.accounts.model.User;
import com.example.accounts.model.responses.LoginResponses;
import com.example.accounts.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class CookieAuthenticationTest {
    private static final String SECRET;
    static {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        SECRET = Base64.getEncoder().encodeToString(bytes);
    }
    private static final String INTERNAL_TOKEN = "test-internal-token-0123456789abcdef";

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private User user;
    private JWTUtilities jwt;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "security.auth-cookie.secure=false",
                "security.cors.allowed-origins=http://localhost:8080",
                "internal.api-token=" + INTERNAL_TOKEN);
        context.register(TestConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        jwt = context.getBean(JWTUtilities.class);
        user = new User();
        user.setId(12L);
        user.setEmail("test@example.com");
        user.setRole(User.Role.USER);
        user.setEmailVerified(true);
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.of(user));
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void signedCookieAuthenticatesByIdAndRequestCannotOverrideIdentity() throws Exception {
        String token = jwt.generateJWTtoken(new MyUserDetails(user));
        assertEquals("12", jwt.parseAndValidate(token).getSubject());
        var response = mvc.perform(get("/api/profile").param("userId", "99")
                .cookie(new Cookie("used_auth", token))).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("12", response.getContentAsString());
    }

    @Test
    void bearerAndQueryParameterAreNotAuthentication() throws Exception {
        String token = jwt.generateJWTtoken(new MyUserDetails(user));
        assertEquals(401, mvc.perform(get("/api/profile").param("token", token)
                .header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus());
    }

    @Test
    void bannedUnverifiedAndDeletedAccountsAreRejected() throws Exception {
        Cookie cookie = new Cookie("used_auth", jwt.generateJWTtoken(new MyUserDetails(user)));
        user.setBanned(true);
        assertEquals(401, mvc.perform(get("/api/profile").cookie(cookie)).andReturn().getResponse().getStatus());
        user.setBanned(false);
        user.setEmailVerified(false);
        assertEquals(401, mvc.perform(get("/api/profile").cookie(cookie)).andReturn().getResponse().getStatus());
        when(context.getBean(UserRepository.class).findById(12L)).thenReturn(Optional.empty());
        assertEquals(401, mvc.perform(get("/api/profile").cookie(cookie)).andReturn().getResponse().getStatus());
    }

    @Test
    void invalidCookieIsClearedWithoutBlockingCsrfBootstrap() throws Exception {
        var response = mvc.perform(get("/auth/user/csrf")
                .cookie(new Cookie("used_auth", "invalid"))).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertTrue(response.getHeaders("Set-Cookie").stream()
                .anyMatch(value -> value.startsWith("used_auth=") && value.contains("Max-Age=0")));
    }

    @Test
    void csrfRequiredForLoginAndAuthenticatedWrites() throws Exception {
        assertEquals(403, mvc.perform(post("/auth/user/login")).andReturn().getResponse().getStatus());
        var bootstrap = mvc.perform(get("/auth/user/csrf")).andReturn().getResponse();
        Cookie csrfCookie = bootstrap.getCookie("used_csrf");
        assertNotNull(csrfCookie);
        String csrfToken = bootstrap.getContentAsString();
        assertEquals(200, mvc.perform(post("/auth/user/login").cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfToken)).andReturn().getResponse().getStatus());
        Cookie auth = new Cookie("used_auth", jwt.generateJWTtoken(new MyUserDetails(user)));
        assertEquals(403, mvc.perform(post("/api/profile").cookie(auth)).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post("/api/profile").cookie(auth, csrfCookie)
                .header("X-XSRF-TOKEN", "wrong")).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(post("/api/profile").cookie(auth, csrfCookie)
                .header("X-XSRF-TOKEN", csrfToken)).andReturn().getResponse().getStatus());
    }

    @Test
    void onlyConfiguredFrontendOriginReceivesCredentialedCors() throws Exception {
        var allowed = mvc.perform(options("/auth/user/login")
                .header("Origin", "http://localhost:8080")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andReturn().getResponse();
        assertEquals(200, allowed.getStatus());
        assertEquals("true", allowed.getHeader("Access-Control-Allow-Credentials"));
        assertEquals(403, mvc.perform(options("/auth/user/login")
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")).andReturn().getResponse().getStatus());
    }

    @Test
    void rejectsExpiredWrongIssuerWrongAudienceAndMissingClaims() {
        long now = System.currentTimeMillis();
        assertThrows(JwtException.class, () -> jwt.parseAndValidate(token("used-accounts", "used-api", now - 120_000, now - 60_000, "12")));
        assertThrows(JwtException.class, () -> jwt.parseAndValidate(token("other", "used-api", now, now + 60_000, "12")));
        assertThrows(JwtException.class, () -> jwt.parseAndValidate(token("used-accounts", "other", now, now + 60_000, "12")));
        assertThrows(JwtException.class, () -> jwt.parseAndValidate(token("used-accounts", "used-api", now, now + 60_000, "email@example.com")));
        String missing = Jwts.builder().setSubject("12").setIssuer("used-accounts")
                .setAudience("used-api").signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256).compact();
        assertThrows(JwtException.class, () -> jwt.parseAndValidate(missing));
        JWTUtilities otherKey = new JWTUtilities(Base64.getEncoder().encodeToString(new byte[32]),
                "used-accounts", "used-api", Duration.ofMinutes(15));
        assertThrows(JwtException.class, () -> jwt.parseAndValidate(otherKey.generateJWTtoken(new MyUserDetails(user))));
    }

    @Test
    void internalLookupsRequireTheServiceTokenNotAUserCookie() throws Exception {
        String cookie = jwt.generateJWTtoken(new MyUserDetails(user));

        // A valid browser session is not enough, and a missing token is rejected by the filter.
        assertEquals(401, mvc.perform(get("/api/internal/users/12")
                .cookie(new Cookie("used_auth", cookie))).andReturn().getResponse().getStatus());

        assertEquals(401, mvc.perform(get("/api/internal/users/12")
                .header("X-Internal-Token", "wrong")).andReturn().getResponse().getStatus());

        var accepted = mvc.perform(get("/api/internal/users/12")
                .header("X-Internal-Token", INTERNAL_TOKEN)).andReturn().getResponse();
        assertEquals(200, accepted.getStatus());
        assertEquals("internal 12", accepted.getContentAsString());
    }

    @Test
    void jwtNeverAppearsInLoginJsonAndCookieIsHttpOnly() {
        String json = JsonMapper.builder().build().writeValueAsString(new LoginResponses("secret-jwt", "USER"));
        assertFalse(json.contains("secret-jwt"));
        assertFalse(json.contains("\"token\""));
        var cookies = new AuthCookieService("used_auth", true, Duration.ofMinutes(15));
        var issued = cookies.issue("secret-jwt");
        assertTrue(issued.isHttpOnly());
        assertTrue(issued.isSecure());
        assertEquals("Lax", issued.getSameSite());
        assertEquals("/", issued.getPath());
        assertEquals(Duration.ofMinutes(15), issued.getMaxAge());
        assertEquals(Duration.ZERO, cookies.clear().getMaxAge());
        assertEquals(issued.getPath(), cookies.clear().getPath());
    }

    private String token(String issuer, String audience, long issued, long expires, String subject) {
        return Jwts.builder().setSubject(subject).setIssuer(issuer).setAudience(audience)
                .setIssuedAt(new Date(issued)).setExpiration(new Date(expires))
                .claim("roles", List.of("USER"))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256).compact();
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfiguration.class)
    static class TestConfiguration {
        @Bean UserRepository repository() { return mock(UserRepository.class); }
        @Bean MyUserDetailsService users(UserRepository repository) { return new MyUserDetailsService(repository); }
        @Bean JWTUtilities jwt() { return new JWTUtilities(SECRET, "used-accounts", "used-api", Duration.ofMinutes(15)); }
        @Bean AuthCookieService cookies() { return new AuthCookieService("used_auth", false, Duration.ofMinutes(15)); }
        @Bean ProbeController probe() { return new ProbeController(); }
    }

    // Exercises the real security chain without unrelated, still-unmigrated business services.
    @RestController
    static class ProbeController {
        @GetMapping("/auth/user/csrf") String csrf(CsrfToken token) { return token.getToken(); }
        @PostMapping("/auth/user/login") String login() { return "accepted"; }
        @GetMapping("/api/profile") String profile(@AuthenticationPrincipal MyUserDetails user) {
            return user.getUser().getId().toString();
        }
        @PostMapping("/api/profile") String update() { return "updated"; }
        @GetMapping("/api/internal/users/{id}") String internalUser(@PathVariable Long id) {
            return "internal " + id;
        }
    }
}
