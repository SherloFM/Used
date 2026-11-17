package com.example.accounts.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JWTRequestFilter extends OncePerRequestFilter {
    private final MyUserDetailsService users;
    private final JWTUtilities jwt;
    private final AuthCookieService cookies;
    private final AccountStatusUserDetailsChecker checker = new AccountStatusUserDetailsChecker();

    public JWTRequestFilter(MyUserDetailsService users, JWTUtilities jwt, AuthCookieService cookies) {
        this.users = users;
        this.jwt = jwt;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = cookies.readToken(request);
        if (token != null) {
            try {
                Long id = Long.valueOf(jwt.parseAndValidate(token).getSubject());
                MyUserDetails details = users.loadUserById(id);
                checker.check(details);
                var authentication = new UsernamePasswordAuthenticationToken(
                        details, null, details.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (JwtException | AuthenticationException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
                // An expired cookie must not prevent login, CSRF bootstrap or logout.
                // Protected routes still receive 401 from the security chain.
                response.addHeader("Set-Cookie", cookies.clear().toString());
            }
        }
        chain.doFilter(request, response);
    }
}
