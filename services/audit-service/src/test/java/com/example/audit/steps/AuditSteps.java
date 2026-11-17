package com.example.audit.steps;

import io.cucumber.java.en.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

public class AuditSteps {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    private MvcResult lastResult;

    @When("an unauthenticated user requests audit activity")
    public void requestAuditActivity() throws Exception {
        lastResult = mockMvc.perform(get("/api/internal/audit/activity/1")).andReturn();
    }

    @When("a client requests internal audit log without token")
    public void requestInternalAudit() throws Exception {
        lastResult = mockMvc.perform(post("/api/internal/audit/log")
                .contentType("application/json")
                .content("{}")).andReturn();
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expected) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(expected);
    }
}
