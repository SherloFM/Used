package com.example.marketplace.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AccountsClientTest {

    private static final String TOKEN = "test-internal-token-0123456789abcdef";

    private MockRestServiceServer server;
    private AccountsClient client;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://accounts.test")
                .defaultHeader("X-Internal-Token", TOKEN);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AccountsClient(builder.build());
    }

    @Test
    void mapsUserSummaryAndSendsTheServiceToken() {
        server.expect(requestTo("http://accounts.test/api/internal/users/12"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andRespond(withSuccess(
                        "{\"id\":12,\"username\":\"ahmed\",\"email\":\"a@b.c\",\"role\":\"USER\"}",
                        MediaType.APPLICATION_JSON));

        UserSummary user = client.find(12L).orElseThrow();

        assertEquals(12L, user.id());
        assertEquals("ahmed", user.username());
        assertEquals("a@b.c", user.email());
        assertEquals("USER", user.role());
        server.verify();
    }

    @Test
    void unknownUserIsEmptyNotAnError() {
        server.expect(requestTo("http://accounts.test/api/internal/users/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertTrue(client.find(999L).isEmpty());
    }

    @Test
    void rejectedCallFailsLoudly() {
        server.expect(requestTo("http://accounts.test/api/internal/users/12"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(IllegalStateException.class, () -> client.find(12L));
    }

    @Test
    void nullIdNeverCallsAccounts() {
        assertTrue(client.find(null).isEmpty());
        server.verify();
    }
}
