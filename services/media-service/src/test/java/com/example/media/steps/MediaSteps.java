package com.example.media.steps;

import io.cucumber.java.en.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.assertj.core.api.Assertions.assertThat;

public class MediaSteps {

    @Autowired
    private MockMvc mockMvc;

    private MvcResult lastResult;
    private byte[] imageBytes;

    @Given("a valid PNG image of {int}x{int} pixels")
    public void aValidPngImage(int w, int h) throws IOException {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        imageBytes = out.toByteArray();
    }

    @Given("a text file named {string}")
    public void aTextFile(String name) {
        imageBytes = "not an image".getBytes();
    }

    @Given("a file larger than 5 MB")
    public void aFileLargerThan5MB() {
        imageBytes = new byte[5 * 1024 * 1024 + 1];
    }

    @When("the image is uploaded with ownerId {int}")
    public void uploadImage(int ownerId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", imageBytes);
        lastResult = mockMvc.perform(multipart("/api/internal/media")
                .file(file)
                .param("ownerId", String.valueOf(ownerId))
                .header("X-Internal-Token", "test-internal-token-at-least-32-characters"))
                .andReturn();
    }

    @When("the file is uploaded with ownerId {int}")
    public void uploadFile(int ownerId) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "file.png", "image/png", imageBytes);
        lastResult = mockMvc.perform(multipart("/api/internal/media")
                .file(file)
                .param("ownerId", String.valueOf(ownerId))
                .header("X-Internal-Token", "test-internal-token-at-least-32-characters"))
                .andReturn();
    }

    @Then("the response status is {int}")
    public void responseStatusIs(int expected) {
        assertThat(lastResult.getResponse().getStatus()).isEqualTo(expected);
    }

    @Then("the response contains a url starting with {string}")
    public void responseContainsUrl(String prefix) throws Exception {
        String body = lastResult.getResponse().getContentAsString();
        assertThat(body).contains("\"url\"");
        assertThat(body).contains(prefix);
    }

    @Then("the response contentType is {string}")
    public void responseContentTypeIs(String expected) throws Exception {
        String body = lastResult.getResponse().getContentAsString();
        assertThat(body).contains("\"contentType\":\"" + expected + "\"");
    }
}
