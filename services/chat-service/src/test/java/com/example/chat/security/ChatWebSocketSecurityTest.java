package com.example.chat.security;

import com.example.chat.config.WebSocketConfig;
import org.junit.jupiter.api.*;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.messaging.web.csrf.CsrfChannelInterceptor;
import org.springframework.security.messaging.web.socket.server.CsrfTokenHandshakeInterceptor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatWebSocketSecurityTest {
    private final AuthorizationManager<Message<?>> manager =
            new WebSocketSecurityConfiguration().messageAuthorizationManager(
                    new MessageMatcherDelegatingAuthorizationManager.Builder());

    private Authentication user() {
        return new UsernamePasswordAuthenticationToken(new CurrentActor(12L, "alice", "USER"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private Message<byte[]> frame(StompCommand command, String destination, Authentication authentication) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) accessor.setDestination(destination);
        accessor.setUser(authentication);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> connect(Map<String, Object> sessionAttributes, String nativeToken) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setUser(user());
        if (sessionAttributes != null) accessor.setSessionAttributes(sessionAttributes);
        if (nativeToken != null) accessor.addNativeHeader("X-XSRF-TOKEN", nativeToken);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> otherFrame() {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.OTHER);
        accessor.setUser(user());
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private boolean allowed(Message<byte[]> message, Authentication authentication) {
        return manager.authorize(() -> authentication, message).isGranted();
    }

    @Test
    void connectRequiresAnAuthenticatedPrincipal() {
        assertTrue(allowed(connect(null, null), user()));
        assertFalse(allowed(connect(null, null), null));
    }

    @Test
    void onlyTheApplicationSendDestinationIsAccepted() {
        assertTrue(allowed(frame(StompCommand.SEND, "/app/chat.send", user()), user()));
        assertFalse(allowed(frame(StompCommand.SEND, "/topic/room.7", user()), user()));
        assertFalse(allowed(frame(StompCommand.SEND, "/queue/messages", user()), user()));
        assertFalse(allowed(frame(StompCommand.SEND, "/app/chat.send", null), null));
    }

    @Test
    void onlyTheUsersPrivateQueueCanBeSubscribedTo() {
        assertTrue(allowed(frame(StompCommand.SUBSCRIBE, "/user/queue/messages", user()), user()));
        assertFalse(allowed(frame(StompCommand.SUBSCRIBE, "/topic/room.7", user()), user()));
        assertFalse(allowed(frame(StompCommand.SUBSCRIBE, "/queue/messages", user()), user()));
        assertFalse(allowed(frame(StompCommand.SUBSCRIBE, "/user/queue/messages", null), null));
    }

    @Test
    void framesWithoutAnAllowedRuleAreRejected() {
        assertTrue(allowed(frame(StompCommand.DISCONNECT, null, user()), user()));
        assertFalse(allowed(frame(StompCommand.MESSAGE, null, user()), user()));
        assertFalse(allowed(otherFrame(), user()));
    }

    @Test
    void connectFramesMustCarryTheHandshakeCsrfToken() {
        CsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "csrf-token-value");
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(CsrfToken.class.getName(), token);
        var interceptor = new CsrfChannelInterceptor();

        assertThrows(InvalidCsrfTokenException.class,
                () -> interceptor.preSend(connect(attributes, null), null));
        assertThrows(InvalidCsrfTokenException.class,
                () -> interceptor.preSend(connect(attributes, "not-the-token"), null));

        Message<byte[]> valid = connect(attributes, "csrf-token-value");
        assertSame(valid, interceptor.preSend(valid, null));

        assertThrows(MissingCsrfTokenException.class, () -> interceptor.preSend(connect(null, "any"), null));
    }

    @Test
    void handshakeCopiesTheDeferredTokenIntoTheWebSocketSession() {
        CsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "csrf-token-value");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DeferredCsrfToken.class.getName(), new DeferredCsrfToken() {
            @Override public CsrfToken get() { return token; }
            @Override public boolean isGenerated() { return false; }
        });
        Map<String, Object> attributes = new HashMap<>();
        assertTrue(new CsrfTokenHandshakeInterceptor().beforeHandshake(
                new ServletServerHttpRequest(request), new ServletServerHttpResponse(new MockHttpServletResponse()),
                null, attributes));
        assertEquals("csrf-token-value",
                ((CsrfToken) attributes.get(CsrfToken.class.getName())).getToken());

        Map<String, Object> empty = new HashMap<>();
        assertTrue(new CsrfTokenHandshakeInterceptor().beforeHandshake(
                new ServletServerHttpRequest(new MockHttpServletRequest()),
                new ServletServerHttpResponse(new MockHttpServletResponse()), null, empty));
        assertTrue(empty.isEmpty());
    }

    @Test
    void wildcardFrontendOriginsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WebSocketConfig("*"));
        assertThrows(IllegalArgumentException.class, () -> new WebSocketConfig("http://localhost:8080, *"));
    }
}
