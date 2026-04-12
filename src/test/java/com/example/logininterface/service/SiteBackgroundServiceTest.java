package com.example.logininterface.service;

import com.example.logininterface.config.AppProperties;
import com.example.logininterface.domain.SiteSetting;
import com.example.logininterface.repository.SiteSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteBackgroundServiceTest {

    @Mock
    private SiteSettingRepository siteSettingRepository;

    @Mock
    private AppProperties appProperties;

    @Test
    void resolveDisplayImageReturnsSelectedImageWhenRandomDisabled() {
        when(appProperties.getDefaultLoginBackground()).thenReturn("https://example.com/default.jpg");
        when(siteSettingRepository.findById(anyString())).thenReturn(Optional.empty());
        mockSetting("login_bg_config", """
                {"images":["/uploads/login-backgrounds/a.jpg","/uploads/login-backgrounds/b.jpg"],"selectedImage":"/uploads/login-backgrounds/b.jpg","randomEnabled":false}
                """);

        SiteBackgroundService service = new SiteBackgroundService(siteSettingRepository, appProperties, new ObjectMapper());

        assertThat(service.resolveDisplayImage()).isEqualTo("/uploads/login-backgrounds/b.jpg");
    }

    @Test
    void resolveDisplayImagePicksOneUploadedImageWhenRandomEnabled() {
        when(appProperties.getDefaultLoginBackground()).thenReturn("https://example.com/default.jpg");
        when(siteSettingRepository.findById(anyString())).thenReturn(Optional.empty());
        mockSetting("login_bg_config", """
                {"images":["/uploads/login-backgrounds/a.jpg","/uploads/login-backgrounds/b.jpg"],"selectedImage":"/uploads/login-backgrounds/a.jpg","randomEnabled":true}
                """);

        SiteBackgroundService service = new SiteBackgroundService(siteSettingRepository, appProperties, new ObjectMapper());

        assertThat(service.resolveDisplayImage())
                .isIn("/uploads/login-backgrounds/a.jpg", "/uploads/login-backgrounds/b.jpg");
    }

    private void mockSetting(String key, String value) {
        SiteSetting setting = new SiteSetting();
        setting.setSettingKey(key);
        setting.setValue(value);
        when(siteSettingRepository.findById(key)).thenReturn(Optional.of(setting));
    }
}
