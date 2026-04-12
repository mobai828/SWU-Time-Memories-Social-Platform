package com.example.logininterface.web;

import com.example.logininterface.service.SiteBackgroundService;
import com.example.logininterface.repository.SiteSettingRepository;
import com.example.logininterface.domain.SiteSetting;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class AdminController {

    private final SiteBackgroundService siteBackgroundService;
    private final SiteSettingRepository siteSettingRepository;

    public AdminController(SiteBackgroundService siteBackgroundService, SiteSettingRepository siteSettingRepository) {
        this.siteBackgroundService = siteBackgroundService;
        this.siteSettingRepository = siteSettingRepository;
    }

    @GetMapping("/admin/backgrounds")
    public String adminBackgrounds() {
        return "redirect:/profile";
    }

    @PostMapping("/admin/backgrounds/upload")
    public String uploadBackgrounds(
            @RequestParam("files") List<MultipartFile> files,
            RedirectAttributes redirectAttributes
    ) {
        try {
            var summary = siteBackgroundService.uploadBackgrounds(files);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "上传完成：新增 " + summary.addedCount() + " 张，复用 " + summary.reusedCount() + " 张，跳过 " + summary.skippedCount() + " 张。"
            );
            if (!summary.messages().isEmpty()) {
                redirectAttributes.addFlashAttribute("detailMessages", summary.messages());
            }
        } catch (ValidationException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/profile";
    }

    @ResponseBody
    @PostMapping("/admin/backgrounds/upload-async")
    public ResponseEntity<BackgroundUploadResponse> uploadBackgroundsAsync(
            @RequestParam("files") List<MultipartFile> files
    ) {
        try {
            var summary = siteBackgroundService.uploadBackgrounds(files);
            return ResponseEntity.ok(new BackgroundUploadResponse(
                    true,
                    "上传完成：新增 " + summary.addedCount() + " 张，复用 " + summary.reusedCount() + " 张，跳过 " + summary.skippedCount() + " 张。",
                    summary.addedCount(),
                    summary.reusedCount(),
                    summary.skippedCount(),
                    0,
                    summary.messages(),
                    "/profile"
            ));
        } catch (ValidationException exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new BackgroundUploadResponse(
                    false,
                    exception.getMessage(),
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    "/profile"
            ));
        }
    }

    @PostMapping("/admin/backgrounds/external")
    public String addExternalBackground(
            @RequestParam("imageUrl") String imageUrl,
            RedirectAttributes redirectAttributes
    ) {
        try {
            siteBackgroundService.addExternalBackground(imageUrl);
            redirectAttributes.addFlashAttribute("successMessage", "外链背景已添加");
        } catch (ValidationException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/admin/backgrounds/remove")
    public String removeBackground(
            @RequestParam("imageUrl") String imageUrl,
            RedirectAttributes redirectAttributes
    ) {
        siteBackgroundService.removeBackground(imageUrl);
        redirectAttributes.addFlashAttribute("successMessage", "背景图已移除");
        return "redirect:/profile";
    }

    @PostMapping("/admin/backgrounds/settings")
    public String saveSettings(
            @RequestParam(value = "selectedImage", required = false) String selectedImage,
            @RequestParam(value = "randomEnabled", defaultValue = "false") boolean randomEnabled,
            RedirectAttributes redirectAttributes
    ) {
        siteBackgroundService.updateSettings(selectedImage, randomEnabled);
        redirectAttributes.addFlashAttribute("successMessage", "登录背景设置已保存");
        return "redirect:/profile";
    }

    @PostMapping("/admin/profile-settings")
    public String saveProfileSettings(
            @RequestParam(value = "profileUpdateDailyLimit") int profileUpdateDailyLimit,
            RedirectAttributes redirectAttributes
    ) {
        if (profileUpdateDailyLimit < 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "修改次数限制不能小于0");
            return "redirect:/profile";
        }
        
        SiteSetting setting = siteSettingRepository.findById("profile_update_daily_limit").orElseGet(SiteSetting::new);
        setting.setSettingKey("profile_update_daily_limit");
        setting.setValue(String.valueOf(profileUpdateDailyLimit));
        siteSettingRepository.save(setting);
        
        redirectAttributes.addFlashAttribute("successMessage", "全局设置已保存");
        return "redirect:/profile";
    }

    public record BackgroundUploadResponse(
            boolean success,
            String message,
            int addedCount,
            int reusedCount,
            int skippedCount,
            int failedCount,
            List<String> detailMessages,
            String redirectUrl
    ) {
    }
}
