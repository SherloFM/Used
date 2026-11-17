package com.example.e2e.pages;

import com.example.e2e.config.TestConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class ProfilePage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By avatarImage = By.id("avatarImg");
    private final By fileInput = By.cssSelector("input[type='file']");
    private final By uploadButton = By.cssSelector("button[type='submit'], .upload-btn, #uploadBtn");
    private final By toast = By.cssSelector(".toast, .notification, [role='status']");

    public ProfilePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public void open() {
        driver.get(TestConfig.BASE_URL + "/profile");
    }

    public void uploadImage(String filePath) {
        WebElement input = wait.until(ExpectedConditions.presenceOfElementLocated(fileInput));
        input.sendKeys(filePath);
        try {
            driver.findElement(uploadButton).click();
        } catch (Exception ignored) {
            // Some forms auto-submit on file selection.
        }
    }

    public boolean avatarIsVisible() {
        try {
            WebElement img = wait.until(ExpectedConditions.visibilityOfElementLocated(avatarImage));
            String src = img.getAttribute("src");
            return src != null && !src.isBlank() && !src.contains("data:,");
        } catch (Exception e) {
            return false;
        }
    }

    public String avatarSrc() {
        try {
            return driver.findElement(avatarImage).getAttribute("src");
        } catch (Exception e) {
            return null;
        }
    }

    public boolean successToastShown() {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.visibilityOfElementLocated(toast)) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
