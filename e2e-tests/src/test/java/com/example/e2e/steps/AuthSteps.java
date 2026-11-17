package com.example.e2e.steps;

import com.example.e2e.config.TestConfig;
import com.example.e2e.config.WebDriverConfig;
import com.example.e2e.pages.LoginPage;
import com.example.e2e.pages.ProfilePage;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.WebDriver;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuthSteps {

    private WebDriver driver;
    private LoginPage loginPage;
    private ProfilePage profilePage;

    @Before
    public void setUp() {
        driver = WebDriverConfig.create();
        loginPage = new LoginPage(driver);
        profilePage = new ProfilePage(driver);
    }

    @After
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Given("the user is on the login page")
    public void theUserIsOnTheLoginPage() {
        loginPage.open();
    }

    @When("the user logs in with valid credentials")
    public void theUserLogsInWithValidCredentials() {
        loginPage.login(TestConfig.USERNAME, TestConfig.PASSWORD);
    }

    @Then("the user is redirected away from the login page")
    public void theUserIsRedirectedAwayFromTheLoginPage() {
        loginPage.waitForRedirectAwayFromLogin();
        assertTrue(!driver.getCurrentUrl().contains("/login"),
                "Expected redirect away from /login but stayed on " + driver.getCurrentUrl());
    }

    @Then("the profile page loads")
    public void theProfilePageLoads() {
        profilePage.open();
        assertTrue(driver.getCurrentUrl().contains("/profile"),
                "Expected /profile but got " + driver.getCurrentUrl());
    }
}
