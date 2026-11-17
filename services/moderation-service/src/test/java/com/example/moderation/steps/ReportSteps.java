package com.example.moderation.steps;

import io.cucumber.java.en.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

public class ReportSteps {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    private MvcResult lastResult;

    @When("an unauthenticated user submits a report for listing {int}")
    public void submitReport(int listingId) throws Exception {
        lastResult = mockMvc.perform(post("/api/reports")
                .contentType("application/json")
                .content("{\"listingId\":" + listingId + ",\"reason\":\"SPAM\"}")).andReturn();
    }

    @When("an unauthenticated user lists their reports")
    public void listReports() throws Exception {
        lastResult = mockMvc.perform(get("/api/reports/mine")).andReturn();
    }

    @When("an unauthenticated user accesses admin reports")
    public void adminReports() throws Exception {
        lastResult = mockMvc.perform(get("/api/admin/reports")).andReturn();
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expected) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(expected);
    }
}
