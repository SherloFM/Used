package com.example.Used.Controller;

import ch.qos.logback.core.model.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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
}
