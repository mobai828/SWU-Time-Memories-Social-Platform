package com.example.logininterface.web;

import com.example.logininterface.service.AuthService;
import com.example.logininterface.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ValidationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes
    ) {
        try {
            authService.register(username, email, password, confirmPassword);
            redirectAttributes.addFlashAttribute("successMessage", "注册成功，请使用新账号登录");
            return "redirect:/login";
        } catch (ValidationException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("registerUsername", username);
            redirectAttributes.addFlashAttribute("registerEmail", email);
            return "redirect:/register";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(
            @RequestParam String email,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        try {
            var token = passwordResetService.createResetToken(email);
            redirectAttributes.addFlashAttribute("successMessage", "如果邮箱存在，系统已生成重置链接。");
            token.ifPresent(value -> redirectAttributes.addFlashAttribute(
                    "devResetLink",
                    request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                            + "/reset-password?token=" + value
            ));
        } catch (ValidationException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        redirectAttributes.addFlashAttribute("forgotEmail", email);
        return "redirect:/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("tokenValid", passwordResetService.getValidToken(token).isPresent());
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(
            @RequestParam String token,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes
    ) {
        try {
            passwordResetService.resetPassword(token, password, confirmPassword);
            redirectAttributes.addFlashAttribute("successMessage", "密码已重置，请重新登录");
            return "redirect:/login";
        } catch (ValidationException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/reset-password?token=" + token;
        }
    }
}
