package com.example.accounts.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalTokenFilterTest {

    private static final String TOKEN = "test-internal-token-0123456789abcdef";

    private final InternalTokenFilter filter = new InternalTokenFilter(TOKEN);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingHeaderIsRejectedAndNeverReachesTheController() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/users/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void wrongHeaderIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/users/1");
        request.addHeader(InternalTokenFilter.HEADER, "test-internal-token-0123456789abcdeF");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test
    void correctHeaderAuthenticatesAsInternal() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/users/1");
        request.addHeader(InternalTokenFilter.HEADER, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.getRequest() != null);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, authentication);
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_INTERNAL".equals(authority.getAuthority())));
    }

    @Test
    void publicAndAdminPathsAreNotFiltered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertTrue(chain.getRequest() != null);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(response.getStatus() == 200);
    }

    @Test
    void shortConfiguredTokenFailsAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new InternalTokenFilter("too-short"));
    }
}
