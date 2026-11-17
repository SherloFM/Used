package com.example.marketplace.steps;

import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

public class ListingSteps {

    @Autowired
    private MockMvc mockMvc;

    private MvcResult lastResult;

    @When("the client requests all categories")
    public void requestAllCategories() throws Exception {
        lastResult = mockMvc.perform(get("/api/categories")).andReturn();
    }

    @When("the client searches listings with keyword {string}")
    public void searchListings(String keyword) throws Exception {
        lastResult = mockMvc.perform(get("/api/listings/search")
                .param("keyword", keyword)).andReturn();
    }

    @When("the client requests listing with id {int}")
    public void requestListing(int id) throws Exception {
        lastResult = mockMvc.perform(get("/api/listings/" + id)).andReturn();
    }

    @When("an unauthenticated user creates a listing")
    public void createListingUnauth() throws Exception {
        lastResult = mockMvc.perform(post("/api/listings")
                .contentType("application/json")
                .content("{\"title\":\"Test\",\"description\":\"Desc\",\"price\":10,\"condition\":\"New\",\"location\":\"Bahrain\"}"))
                .andReturn();
    }

    @When("an unauthenticated user requests their listings")
    public void requestMyListings() throws Exception {
        lastResult = mockMvc.perform(get("/api/listings/mine")).andReturn();
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expected) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(expected);
    }
}
