package com.example.notification.steps;

import io.cucumber.java.en.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

public class NotificationSteps {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    private MvcResult lastResult;

    @When("an unauthenticated user lists notifications")
    public void listNotifications() throws Exception {
        lastResult = mockMvc.perform(get("/api/notifications")).andReturn();
    }

    @When("an unauthenticated user gets unread count")
    public void unreadCount() throws Exception {
        lastResult = mockMvc.perform(get("/api/notifications/unread-count")).andReturn();
    }

    @When("an unauthenticated user marks all notifications read")
    public void markAllRead() throws Exception {
        lastResult = mockMvc.perform(put("/api/notifications/read-all")).andReturn();
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expected) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(expected);
    }
}
