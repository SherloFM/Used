package com.example.media.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

class InternalTokenFilterTest {

    private static final String TOKEN = "test-internal-token-at-least-32-characters";

    @Test
    void validTokenAuthenticatesInternalRequest() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/internal/media");
        request.addHeader("X-Internal-Token", TOKEN);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        new InternalTokenFilter(TOKEN).doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertEquals("ROLE_INTERNAL", SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority());
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingOrWrongTokenIsRejected() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        new InternalTokenFilter(TOKEN).doFilter(
                new MockHttpServletRequest("POST", "/api/internal/media"), response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void publicReadsAreNotFiltered() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/media/70d85413-78d7-407f-9562-5df075cf92cb");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        new InternalTokenFilter(TOKEN).doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void shortTokenIsRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new InternalTokenFilter("short"));
    }
}
