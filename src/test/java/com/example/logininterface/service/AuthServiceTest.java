package com.example.logininterface.service;

import com.example.logininterface.config.AppProperties;
import com.example.logininterface.domain.UserAccount;
import com.example.logininterface.repository.UserAccountRepository;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AppProperties appProperties;

    @Mock
    private BaiduCensorService baiduCensorService;

    @InjectMocks
    private AuthService authService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateProfileUpdatesUsernameAndAvatarUrlForCurrentUser() {
        UserAccount currentUser = new UserAccount();
        currentUser.setId(1L);
        currentUser.setEmail("demo@example.com");
        currentUser.setUsername("old-name");

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("demo@example.com", null, java.util.List.of())
        );
        when(userAccountRepository.findByEmailIgnoreCase("demo@example.com")).thenReturn(Optional.of(currentUser));
        when(userAccountRepository.findByUsernameIgnoreCase("new-name")).thenReturn(Optional.empty());
        when(baiduCensorService.isTextValid("new-name")).thenReturn(true);
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount updated = authService.updateProfile("new-name", "https://example.com/avatar.png", null);

        assertThat(updated.getUsername()).isEqualTo("new-name");
        assertThat(updated.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
        verify(userAccountRepository).save(currentUser);
    }

    @Test
    void updateProfileRejectsDuplicateUsername() {
        UserAccount currentUser = new UserAccount();
        currentUser.setId(1L);
        currentUser.setEmail("demo@example.com");
        currentUser.setUsername("old-name");

        UserAccount otherUser = new UserAccount();
        otherUser.setId(2L);
        otherUser.setUsername("taken-name");

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("demo@example.com", null, java.util.List.of())
        );
        when(userAccountRepository.findByEmailIgnoreCase("demo@example.com")).thenReturn(Optional.of(currentUser));
        when(userAccountRepository.findByUsernameIgnoreCase("taken-name")).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> authService.updateProfile("taken-name", "", null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("用户名已存在");
    }

    @Test
    void updateProfileStoresWebpAvatarForCurrentUser() throws IOException {
        UserAccount currentUser = new UserAccount();
        currentUser.setId(1L);
        currentUser.setEmail("demo@example.com");
        currentUser.setUsername("old-name");

        MockMultipartFile avatarFile = new MockMultipartFile(
                "avatarFile",
                "avatar.webp",
                "image/webp",
                new byte[]{
                        'R', 'I', 'F', 'F',
                        0x18, 0x00, 0x00, 0x00,
                        'W', 'E', 'B', 'P',
                        'V', 'P', '8', ' '
                }
        );

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("demo@example.com", null, java.util.List.of())
        );
        when(appProperties.getUploadDir()).thenReturn(tempDir.toString());
        when(userAccountRepository.findByEmailIgnoreCase("demo@example.com")).thenReturn(Optional.of(currentUser));
        when(userAccountRepository.findByUsernameIgnoreCase("new-name")).thenReturn(Optional.empty());
        when(baiduCensorService.isTextValid("new-name")).thenReturn(true);
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserAccount updated = authService.updateProfile("new-name", "", avatarFile);

        assertThat(updated.getUsername()).isEqualTo("new-name");
        assertThat(updated.getAvatarUrl()).startsWith("/uploads/avatars/1/").endsWith(".webp");
        Path storedAvatarDirectory = tempDir.resolve("avatars").resolve("1");
        assertThat(Files.exists(storedAvatarDirectory)).isTrue();
        try (Stream<Path> storedFiles = Files.list(storedAvatarDirectory)) {
            assertThat(storedFiles).hasSize(1);
        }
    }
}
