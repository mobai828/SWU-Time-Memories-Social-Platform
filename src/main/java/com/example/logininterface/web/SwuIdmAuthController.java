package com.example.logininterface.web;

import com.example.logininterface.service.AuthService;
import com.example.logininterface.service.SwuIdmAuthService;
import jakarta.validation.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@org.springframework.stereotype.Controller
public class SwuIdmAuthController {

    private final SwuIdmAuthService swuIdmAuthService;
    private final AuthService authService;

    public SwuIdmAuthController(SwuIdmAuthService swuIdmAuthService, AuthService authService) {
        this.swuIdmAuthService = swuIdmAuthService;
        this.authService = authService;
    }

    @GetMapping("/auth/swu")
    public String redirectToSwuLogin(HttpServletRequest request) {
        return "redirect:" + swuIdmAuthService.buildLoginRedirectUrl(request);
    }

    @GetMapping("/login/swu/callback")
    public String handleSwuCallback(
            @RequestParam(required = false) String ticket,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            var campusUser = swuIdmAuthService.authenticate(ticket, request);
            authService.loginSwuUser(request, campusUser);
            redirectAttributes.addFlashAttribute("successMessage", "统一认证登录成功");
            return "redirect:/";
        } catch (ValidationException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/login";
        }
    }
}
