package com.example.logininterface.web;

import com.example.logininterface.domain.UserRole;
import com.example.logininterface.service.AuthService;
import com.example.logininterface.service.SiteBackgroundService;
import com.example.logininterface.service.SocialStatsService;
import jakarta.validation.ValidationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final AuthService authService;
    private final SiteBackgroundService siteBackgroundService;
    private final SocialStatsService socialStatsService;

    public ProfileController(
            AuthService authService,
            SiteBackgroundService siteBackgroundService,
            SocialStatsService socialStatsService
    ) {
        this.authService = authService;
        this.siteBackgroundService = siteBackgroundService;
        this.socialStatsService = socialStatsService;
    }

    @GetMapping("/profile")
    public String profile(Model model) {
        var currentUser = socialStatsService.syncCounters(authService.getRequiredCurrentUser());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isAdmin", currentUser.getRole() == UserRole.ADMIN);
        
        java.time.LocalDate today = java.time.LocalDate.now();
        int usedCount = (currentUser.getLastProfileUpdateDate() != null && currentUser.getLastProfileUpdateDate().equals(today)) 
                ? currentUser.getDailyProfileUpdateCount() : 0;
        int dailyLimit = authService.getProfileUpdateDailyLimit();
        model.addAttribute("dailyLimit", dailyLimit);
        model.addAttribute("remainingCount", Math.max(0, dailyLimit - usedCount));

        if (currentUser.getRole() == UserRole.ADMIN) {
            model.addAttribute("backgroundConfig", siteBackgroundService.getConfig());
        }
        return "profile";
    }

    @PostMapping("/profile/social/follow")
    public String follow(
            @RequestParam("targetUserId") Long targetUserId,
            @RequestParam(value = "returnTo", defaultValue = "/profile") String returnTo,
            RedirectAttributes redirectAttributes
    ) {
        try {
            socialStatsService.follow(authService.getRequiredCurrentUser(), targetUserId);
            redirectAttributes.addFlashAttribute("successMessage", "关注成功");
        } catch (ValidationException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:" + sanitizeReturnTo(returnTo);
    }

    @PostMapping("/profile/social/unfollow")
    public String unfollow(
            @RequestParam("targetUserId") Long targetUserId,
            @RequestParam(value = "returnTo", defaultValue = "/profile") String returnTo,
            RedirectAttributes redirectAttributes
    ) {
        socialStatsService.unfollow(authService.getRequiredCurrentUser(), targetUserId);
        redirectAttributes.addFlashAttribute("successMessage", "已取消关注");
        return "redirect:" + sanitizeReturnTo(returnTo);
    }

    @PostMapping("/profile/social/like")
    public String likeUser(
            @RequestParam("targetUserId") Long targetUserId,
            @RequestParam(value = "returnTo", defaultValue = "/profile") String returnTo,
            RedirectAttributes redirectAttributes
    ) {
        try {
            socialStatsService.likeUser(authService.getRequiredCurrentUser(), targetUserId);
            redirectAttributes.addFlashAttribute("successMessage", "点赞成功");
        } catch (ValidationException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:" + sanitizeReturnTo(returnTo);
    }

    @PostMapping("/profile/social/unlike")
    public String unlikeUser(
            @RequestParam("targetUserId") Long targetUserId,
            @RequestParam(value = "returnTo", defaultValue = "/profile") String returnTo,
            RedirectAttributes redirectAttributes
    ) {
        socialStatsService.unlikeUser(authService.getRequiredCurrentUser(), targetUserId);
        redirectAttributes.addFlashAttribute("successMessage", "已取消点赞");
        return "redirect:" + sanitizeReturnTo(returnTo);
    }

    private String sanitizeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank() || !returnTo.startsWith("/")) {
            return "/profile";
        }
        return returnTo;
    }

    @PostMapping("/profile")
    public String updateProfile(
            @RequestParam String username,
            @RequestParam(defaultValue = "") String signature,
            @RequestParam(defaultValue = "") String avatarUrl,
            @RequestParam(name = "avatarFileRaw", required = false) MultipartFile avatarFileRaw,
            @RequestParam(name = "avatarFileBase64", required = false) String avatarFileBase64,
            RedirectAttributes redirectAttributes
    ) {
        try {
            MultipartFile actualAvatarFile = avatarFileRaw;
            
            // If base64 cropped image is provided, convert it to MultipartFile
            if (avatarFileBase64 != null && !avatarFileBase64.isBlank() && avatarFileBase64.startsWith("data:image/")) {
                String[] parts = avatarFileBase64.split(",");
                if (parts.length == 2) {
                    byte[] decodedBytes = java.util.Base64.getDecoder().decode(parts[1]);
                    // We can't use MockMultipartFile in production code unless spring-test is on compile scope.
                    // To avoid dependency issues, let's implement a simple MultipartFile wrapper.
                    actualAvatarFile = new Base64MultipartFile(decodedBytes, "avatar.jpg", "image/jpeg");
                }
            }

            authService.updateProfile(username, signature, avatarUrl, actualAvatarFile);
            redirectAttributes.addFlashAttribute("successMessage", "个人资料更新成功");
        } catch (ValidationException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("profileUsername", username);
            redirectAttributes.addFlashAttribute("profileSignature", signature);
            redirectAttributes.addFlashAttribute("profileAvatarUrl", avatarUrl);
        }
        return "redirect:/profile";
    }

    public static class Base64MultipartFile implements MultipartFile {
        private final byte[] content;
        private final String name;
        private final String contentType;

        public Base64MultipartFile(byte[] content, String name, String contentType) {
            this.content = content;
            this.name = name;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return name;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws java.io.IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
