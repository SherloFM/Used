package com.example.accounts.steps;

import com.example.accounts.model.User;
import com.example.accounts.repository.UserRepository;
import com.example.accounts.security.JWTUtilities;
import com.example.accounts.security.MyUserDetails;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthenticationSteps {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JWTUtilities jwtUtilities;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private MvcResult lastResult;
    private Cookie authCookie;

    @Before
    public void setUp() {
        lastResult = null;
        authCookie = null;
    }

    @After
    public void tearDown() {
    }

    @Given("no user exists with email {string}")
    public void noUserExistsWithEmail(String email) {
        User existing = userRepository.findByEmail(email);
        if (existing != null) userRepository.delete(existing);
    }

    @When("the user registers with email {string} and password {string}")
    public void register(String email, String password) throws Exception {
        String username = email.split("@")[0];
        String body = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\",\"username\":\"%s\"}",
                email, password, username);
        lastResult = mockMvc.perform(post("/auth/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andReturn();
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expected) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(expected);
    }

    @Then("the response contains a user id")
    public void responseContainsUserId() throws Exception {
        String body = lastResult.getResponse().getContentAsString();
        assertThat(body).contains("\"id\"");
    }

    @Then("the stored password accepts {string}")
    public void storedPasswordAccepts(String rawPassword) {
        User user = userRepository.findByEmail("newuser@test.com");
        assertThat(user).isNotNull();
        assertThat(passwordEncoder.matches(rawPassword, user.getPassword())).isTrue();
    }

    @When("the user verifies the registered email {string}")
    public void verifyRegisteredEmail(String email) throws Exception {
        User user = userRepository.findByEmail(email);
        assertThat(user).isNotNull();
        String body = String.format("{\"email\":\"%s\",\"code\":\"%s\"}",
                email, user.getVerificationCode());
        lastResult = mockMvc.perform(post("/auth/user/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andReturn();
    }

    @Given("a user exists with email {string} and password {string}")
    public void userExists(String email, String password) {
        if (userRepository.findByEmail(email) != null) return;
        User user = new User();
        user.setEmail(email);
        user.setUsername(email.split("@")[0]);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(User.Role.USER);
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @When("the user logs in with email {string} and password {string}")
    public void login(String email, String password) throws Exception {
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        lastResult = mockMvc.perform(post("/auth/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andReturn();

        if (lastResult.getResponse().getStatus() == 200) {
            authCookie = lastResult.getResponse().getCookie("used_auth");
        }
    }

    @Then("the response sets the {string} cookie")
    public void responseSetsCookie(String cookieName) {
        assertThat(lastResult.getResponse().getCookie(cookieName)).isNotNull();
    }

    @When("the client requests a CSRF token")
    public void requestCsrf() throws Exception {
        lastResult = mockMvc.perform(get("/auth/user/csrf")).andReturn();
    }

    @Then("the response contains a token field")
    public void responseContainsToken() throws Exception {
        String body = lastResult.getResponse().getContentAsString();
        assertThat(body).contains("token");
    }

    @When("an authenticated user requests their profile")
    public void authenticatedProfileRequest() throws Exception {
        User user = userRepository.findByEmail("alice@used.local");
        if (user == null) throw new IllegalStateException("alice not found");
        String jwt = jwtUtilities.generateJWTtoken(new com.example.accounts.security.MyUserDetails(user));
        authCookie = new Cookie("used_auth", jwt);

        lastResult = mockMvc.perform(get("/api/profile")
                .cookie(authCookie)
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andReturn();
    }

    @Then("the profile response contains the user email")
    public void profileContainsEmail() throws Exception {
        String body = lastResult.getResponse().getContentAsString();
        assertThat(body).contains("alice@used.local");
    }

    @When("an unauthenticated user requests their profile")
    public void unauthenticatedProfileRequest() throws Exception {
        lastResult = mockMvc.perform(get("/api/profile")).andReturn();
    }
}
