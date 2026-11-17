package com.example.chat.steps;

import io.cucumber.java.en.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

public class ChatSteps {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    private MvcResult lastResult;

    @When("an unauthenticated user lists chat conversations")
    public void listConversations() throws Exception {
        lastResult = mockMvc.perform(get("/api/chat/conversations")).andReturn();
    }

    @When("an unauthenticated user creates a chat conversation")
    public void createConversation() throws Exception {
        lastResult = mockMvc.perform(post("/api/chat/conversation")
                .contentType("application/json")
                .content("{\"otherUserId\":1}")).andReturn();
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expected) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(expected);
    }
}
