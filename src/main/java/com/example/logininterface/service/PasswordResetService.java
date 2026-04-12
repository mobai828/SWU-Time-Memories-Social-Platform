package com.example.logininterface.service;

import com.example.logininterface.config.AppProperties;
import com.example.logininterface.domain.PasswordResetToken;
import com.example.logininterface.domain.UserAccount;
import com.example.logininterface.repository.PasswordResetTokenRepository;
import com.example.logininterface.repository.UserAccountRepository;
import jakarta.validation.ValidationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    public PasswordResetService(
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            AppProperties appProperties
    ) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
    }

    @Transactional
    public Optional<String> createResetToken(String email) {
        if (email == null || email.isBlank()) {
            throw new ValidationException("请输入邮箱地址");
        }

        passwordResetTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        return userAccountRepository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .map(user -> {
                    PasswordResetToken token = new PasswordResetToken();
                    token.setUser(user);
                    token.setToken(UUID.randomUUID().toString().replace("-", ""));
                    token.setExpiresAt(LocalDateTime.now().plusMinutes(appProperties.getResetTokenMinutes()));
                    passwordResetTokenRepository.save(token);
                    return token.getToken();
                });
    }

    public Optional<PasswordResetToken> getValidToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        return passwordResetTokenRepository.findByToken(tokenValue)
                .filter(token -> !token.isExpired() && !token.isUsed());
    }

    @Transactional
    public void resetPassword(String tokenValue, String password, String confirmPassword) {
        PasswordResetToken token = getValidToken(tokenValue)
                .orElseThrow(() -> new ValidationException("重置链接无效或已过期"));

        if (password == null || password.length() < 6) {
            throw new ValidationException("密码长度至少为 6 位");
        }
        if (!password.equals(confirmPassword)) {
            throw new ValidationException("两次输入的密码不一致");
        }

        UserAccount user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(password));
        userAccountRepository.save(user);
        token.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);
    }
}
