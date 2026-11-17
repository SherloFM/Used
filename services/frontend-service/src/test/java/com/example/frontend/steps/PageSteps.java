package com.example.frontend.steps;

import io.cucumber.java.en.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

public class PageSteps {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    private MvcResult lastResult;

    @When("the client requests the home page")
    public void homePage() throws Exception {
        lastResult = mockMvc.perform(get("/")).andReturn();
    }

    @When("the client requests the login page")
    public void loginPage() throws Exception {
        lastResult = mockMvc.perform(get("/login")).andReturn();
    }

    @When("the client requests the profile page")
    public void profilePage() throws Exception {
        lastResult = mockMvc.perform(get("/profile")).andReturn();
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expected) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(expected);
    }
}
