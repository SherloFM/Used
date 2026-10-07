package com.example.Used.Controller;

import ch.qos.logback.core.model.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index"; // Looks for src/main/resources/templates/index.html
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login"; // Maps to templates/login.html
    }

    @GetMapping("/verify")
    public String verifyPage() {
        return "verify";
    }

    @GetMapping("/profile")
    public String profilePage() {
        return "profile"; // Maps to src/main/resources/templates/profile.html
    }

    @GetMapping("/create-listing")
    public String createListingPage() {
        return "create-listing"; // Maps to templates/create-listing.html
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        // IMPORTANT: Return just the filename WITHOUT .html extension
        return "forgot-password";
    }

    @GetMapping("/listing/{id}")
    public String viewListing(@PathVariable Long id, Model model) {
        // We don't fetch data here; JS will do it via API for better SPA feel
        return "listing-detail";
    }
}
