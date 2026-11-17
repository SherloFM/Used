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

public class ImageSteps {

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

    @Given("the user is logged in")
    public void theUserIsLoggedIn() {
        loginPage.open();
        loginPage.login(TestConfig.USERNAME, TestConfig.PASSWORD);
        loginPage.waitForRedirectAwayFromLogin();
    }

    @When("the user opens the profile page")
    public void theUserOpensTheProfilePage() {
        profilePage.open();
    }

    @When("the user uploads a profile image")
    public void theUserUploadsAProfileImage() {
        profilePage.uploadImage(TestConfig.PROFILE_IMAGE_PATH);
    }

    @Then("the profile avatar is displayed")
    public void theProfileAvatarIsDisplayed() {
        assertTrue(profilePage.avatarIsVisible(),
                "Profile avatar was not displayed after upload");
    }

    @Then("the avatar source is a media URL")
    public void theAvatarSourceIsAMediaUrl() {
        String src = profilePage.avatarSrc();
        assertTrue(src != null && src.contains("/api/media/"),
                "Expected /api/media/ in avatar src but got: " + src);
    }
}
