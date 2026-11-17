package com.example.favorites.steps;

import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

public class FavoriteSteps {

    @Autowired
    private MockMvc mockMvc;

    private MvcResult lastResult;

    @When("an unauthenticated user adds listing {int} to favorites")
    public void addFavorite(int listingId) throws Exception {
        lastResult = mockMvc.perform(post("/api/favorites/" + listingId)).andReturn();
    }

    @When("an unauthenticated user checks if listing {int} is favorited")
    public void checkFavorite(int listingId) throws Exception {
        lastResult = mockMvc.perform(get("/api/favorites/" + listingId + "/check")).andReturn();
    }

    @When("the client requests favorite count for listing {int}")
    public void favoriteCount(int listingId) throws Exception {
        lastResult = mockMvc.perform(get("/api/favorites/" + listingId + "/count")).andReturn();
    }

    @When("an unauthenticated user lists their favorites")
    public void listFavorites() throws Exception {
        lastResult = mockMvc.perform(get("/api/favorites")).andReturn();
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expected) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(expected);
    }
}
