package com.example.chat.service;

import com.example.chat.client.*;
import com.example.chat.model.*;
import com.example.chat.model.requests.ChatSendRequest;
import com.example.chat.model.responses.ChatMessageDto;
import com.example.chat.repository.*;
import com.example.chat.security.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    private ChatConversationRepository conversations;
    private ChatMessageRepository messages;
    private AccountsClient accounts;
    private MarketplaceClient marketplace;
    private SimpMessagingTemplate messaging;
    private ChatService service;
    private TransactionTemplate transaction;

    @BeforeEach void setup() {
        conversations = mock(ChatConversationRepository.class);
        messages = mock(ChatMessageRepository.class);
        accounts = mock(AccountsClient.class);
        marketplace = mock(MarketplaceClient.class);
        messaging = mock(SimpMessagingTemplate.class);
        service = new ChatService(conversations, messages, marketplace, accounts, messaging, new CurrentActorProvider());
        transaction = new TransactionTemplate(new DataSourceTransactionManager(
                new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "")));
    }

    private Principal actor(long id) {
        return new UsernamePasswordAuthenticationToken(new CurrentActor(id, "user" + id, "USER"),
                null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private ChatConversation conversation() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L); conversation.setListingId(7L);
        conversation.setBuyerId(12L); conversation.setSellerId(18L);
        when(conversations.findById(1L)).thenReturn(Optional.of(conversation));
        return conversation;
    }

    private ChatSendRequest request() {
        ChatSendRequest request = new ChatSendRequest();
        request.setConversationId(1L); request.setContent("Hello");
        return request;
    }

    @Test void rejectsMissingUntrustedAndUnauthenticatedPrincipals() {
        var provider = new CurrentActorProvider();
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> provider.get(null));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> provider.get(() -> "12"));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> provider.get(
                new UsernamePasswordAuthenticationToken(new CurrentActor(12L,"alice","USER"),null)));
        assertEquals(12L, provider.get(actor(12)).id());
        assertEquals("12", actor(12).getName());
    }

    @Test void outsidersCannotReadSendOrHide() {
        conversation();
        assertThrows(AccessDeniedException.class, () -> service.getMessages(1L,actor(99)));
        assertThrows(AccessDeniedException.class, () -> service.sendMessage(request(),actor(99)));
        assertThrows(AccessDeniedException.class, () -> service.deleteConversation(1L,actor(99)));
        verifyNoInteractions(messages, accounts, marketplace, messaging);
        verify(conversations,never()).save(any());
    }

    @Test void creatingConversationStoresIdsAndReopeningUnhidesOnlyBuyer() {
        when(marketplace.find(7L)).thenReturn(Optional.of(new ListingSummary(7L,18L,"Bike",150.0,"ACTIVE",null)));
        when(accounts.find(18L)).thenReturn(Optional.of(new UserSummary(18L,"seller","seller@example.com","USER")));
        when(conversations.save(any())).thenAnswer(invocation -> {
            ChatConversation c = invocation.getArgument(0); c.setId(1L); return c;
        });
        var dto = service.getOrCreateConversation(7L,actor(12));
        assertEquals(7L,dto.getListingId()); assertEquals("seller",dto.getOtherUserName());
        var capture = ArgumentCaptor.forClass(ChatConversation.class);
        verify(conversations).save(capture.capture());
        ChatConversation stored = capture.getValue();
        assertEquals(12L,stored.getBuyerId()); assertEquals(18L,stored.getSellerId());
        stored.setHiddenFor(12L,true); stored.setHiddenFor(18L,true);
        when(conversations.findByListingIdAndBuyerId(7L,12L)).thenReturn(Optional.of(stored));
        service.getOrCreateConversation(7L,actor(12));
        assertFalse(stored.isHiddenFor(12L)); assertTrue(stored.isHiddenFor(18L));
    }

    @Test void missingListingAndSelfChatAreRejected() {
        assertThrows(ResponseStatusException.class, () -> service.getOrCreateConversation(404L,actor(12)));
        when(marketplace.find(7L)).thenReturn(Optional.of(new ListingSummary(7L,12L,"Bike",150.0,"ACTIVE",null)));
        assertThrows(ResponseStatusException.class, () -> service.getOrCreateConversation(7L,actor(12)));
        verifyNoInteractions(conversations);
    }

    @Test void hiddenConversationsAreOmittedAnd404UsesFallbacks() {
        ChatConversation visible = conversation();
        ChatConversation hidden = new ChatConversation();
        hidden.setBuyerId(12L); hidden.setSellerId(18L); hidden.setHiddenFor(12L,true);
        when(conversations.findByBuyerIdOrSellerIdOrderByLastMessageAtDesc(12L,12L))
                .thenReturn(List.of(visible,hidden));
        var result = service.getMyConversations(actor(12));
        assertEquals(1,result.size());
        assertEquals("Unavailable listing",result.get(0).getListingTitle());
        assertEquals("Deleted user",result.get(0).getOtherUserName());
        service.deleteConversation(1L,actor(12));
        assertTrue(visible.isHiddenFor(12L)); assertFalse(visible.isHiddenFor(18L));
    }

    @Test void deliveryWaitsForCommitAndUsesSeparateViewerDtos() {
        ChatConversation c = conversation(); c.setHiddenFor(18L,true);
        when(accounts.find(12L)).thenReturn(Optional.of(new UserSummary(12L,"alice","alice@example.com","USER")));
        when(messages.save(any())).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0); message.setId(5L); return message;
        });
        transaction.executeWithoutResult(status -> {
            var result = service.sendMessage(request(),actor(12));
            assertTrue(result.isMine()); assertEquals(12L,result.getSenderId());
            assertEquals(1L,result.getConversationId()); assertEquals("alice",result.getSenderName());
            assertFalse(c.isHiddenFor(18L));
            verifyNoInteractions(messaging);
        });
        var recipient = ArgumentCaptor.forClass(ChatMessageDto.class);
        var sender = ArgumentCaptor.forClass(ChatMessageDto.class);
        verify(messaging).convertAndSendToUser(eq("18"),eq("/queue/messages"),recipient.capture());
        verify(messaging).convertAndSendToUser(eq("12"),eq("/queue/messages"),sender.capture());
        assertFalse(recipient.getValue().isMine()); assertTrue(sender.getValue().isMine());
        assertNotSame(recipient.getValue(),sender.getValue());
        assertEquals(5L,recipient.getValue().getId());
    }

    @Test void rollbackSuppressesLiveDelivery() {
        conversation();
        transaction.executeWithoutResult(status -> {
            service.sendMessage(request(),actor(12));
            status.setRollbackOnly();
        });
        verifyNoInteractions(messaging);
    }

    @Test void upstreamFailureDoesNotBecomeDeletedUserOrSaveAMessage() {
        conversation();
        when(accounts.find(12L)).thenThrow(new IllegalStateException("Accounts failed with status 500"));
        assertThrows(IllegalStateException.class, () -> service.sendMessage(request(),actor(12)));
        verifyNoInteractions(messages,messaging);
    }
}
