package com.example.logininterface.config;

import com.example.logininterface.domain.SiteSetting;
import com.example.logininterface.domain.UserAccount;
import com.example.logininterface.domain.UserRole;
import com.example.logininterface.dto.LoginBackgroundConfig;
import com.example.logininterface.repository.SiteSettingRepository;
import com.example.logininterface.repository.UserAccountRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class InitialDataLoader implements CommandLineRunner {

    private final AppProperties appProperties;
    private final UserAccountRepository userAccountRepository;
    private final SiteSettingRepository siteSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public InitialDataLoader(
            AppProperties appProperties,
            UserAccountRepository userAccountRepository,
            SiteSettingRepository siteSettingRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper
    ) {
        this.appProperties = appProperties;
        this.userAccountRepository = userAccountRepository;
        this.siteSettingRepository = siteSettingRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        ensureUploadDirectories();
        ensureAdminAccount();
        ensureSiteSettings();
    }

    private void ensureUploadDirectories() throws IOException {
        Files.createDirectories(Path.of(appProperties.getUploadDir()).resolve("login-backgrounds"));
    }

    private void ensureAdminAccount() {
        if (userAccountRepository.existsByEmailIgnoreCase(appProperties.getAdmin().getEmail())) {
            return;
        }

        UserAccount admin = new UserAccount();
        admin.setUsername(appProperties.getAdmin().getUsername());
        admin.setEmail(appProperties.getAdmin().getEmail().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(appProperties.getAdmin().getPassword()));
        admin.setRole(UserRole.ADMIN);
        userAccountRepository.save(admin);
    }

    private void ensureSiteSettings() throws JsonProcessingException {
        String backgroundUrl = appProperties.getDefaultLoginBackground();

        siteSettingRepository.findById("login_bg_url")
                .orElseGet(() -> saveSetting("login_bg_url", backgroundUrl));

        siteSettingRepository.findById("login_bg_config")
                .orElseGet(() -> {
                    LoginBackgroundConfig config = new LoginBackgroundConfig();
                    config.setImages(List.of(backgroundUrl));
                    config.setSelectedImage(backgroundUrl);
                    config.setRandomEnabled(false);
                    return saveSetting("login_bg_config", writeJson(config));
                });
                
        siteSettingRepository.findById("profile_update_daily_limit")
                .orElseGet(() -> saveSetting("profile_update_daily_limit", "3"));
    }

    private String writeJson(LoginBackgroundConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法初始化背景配置", exception);
        }
    }

    private SiteSetting saveSetting(String key, String value) {
        SiteSetting siteSetting = new SiteSetting();
        siteSetting.setSettingKey(key);
        siteSetting.setValue(value);
        return siteSettingRepository.save(siteSetting);
    }
}
