package com.example.e2e.pages;

import com.example.e2e.config.TestConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class ListingDetailPage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By imageContainer = By.id("imageContainer");
    private final By imageTag = By.cssSelector("#imageContainer img");
    private final By title = By.cssSelector(".listing-title, h1, #listingTitle");

    public ListingDetailPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    public void open(Long listingId) {
        driver.get(TestConfig.BASE_URL + "/listing/" + listingId);
    }

    public boolean imageIsVisible() {
        try {
            WebElement img = wait.until(ExpectedConditions.visibilityOfElementLocated(imageTag));
            String src = img.getAttribute("src");
            return src != null && !src.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    public String imageSrc() {
        try {
            return driver.findElement(imageTag).getAttribute("src");
        } catch (Exception e) {
            return null;
        }
    }

    public String titleText() {
        try {
            return driver.findElement(title).getText();
        } catch (Exception e) {
            return null;
        }
    }
}
