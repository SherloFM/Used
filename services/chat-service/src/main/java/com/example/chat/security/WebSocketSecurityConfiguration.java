package com.example.chat.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfiguration {
    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
                .simpTypeMatchers(SimpMessageType.CONNECT, SimpMessageType.DISCONNECT,
                        SimpMessageType.UNSUBSCRIBE, SimpMessageType.HEARTBEAT).authenticated()
                .simpSubscribeDestMatchers("/user/queue/messages").authenticated()
                .simpTypeMatchers(SimpMessageType.SUBSCRIBE).denyAll()
                .simpDestMatchers("/app/chat.send").authenticated()
                .anyMessage().denyAll();
        return messages.build();
    }
}
