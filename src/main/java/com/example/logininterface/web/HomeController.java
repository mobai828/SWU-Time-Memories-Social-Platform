package com.example.logininterface.web;

import com.example.logininterface.service.AuthService;
import com.example.logininterface.service.SiteBackgroundService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class HomeController {

    private final AuthService authService;
    private final SiteBackgroundService siteBackgroundService;

    public HomeController(AuthService authService, SiteBackgroundService siteBackgroundService) {
        this.authService = authService;
        this.siteBackgroundService = siteBackgroundService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("currentUser", authService.getCurrentUser().orElse(null));
        model.addAttribute("backgroundConfig", siteBackgroundService.getConfig());
        model.addAttribute("displayBackgroundImage", siteBackgroundService.resolveDisplayImage());
        return "home";
    }

    @GetMapping("/api/public/login-background-config")
    @ResponseBody
    public Map<String, Object> getLoginBackgroundConfig() {
        var config = siteBackgroundService.getConfig();
        return Map.of(
                "images", config.getImages(),
                "selectedImage", config.getSelectedImage(),
                "randomEnabled", config.isRandomEnabled()
        );
    }
}
