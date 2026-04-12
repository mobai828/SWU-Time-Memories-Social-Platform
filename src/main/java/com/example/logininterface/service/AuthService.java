package com.example.logininterface.service;

import com.example.logininterface.config.AppProperties;
import com.example.logininterface.domain.UserAccount;
import com.example.logininterface.domain.UserRole;
import com.example.logininterface.repository.UserAccountRepository;
import com.example.logininterface.repository.SiteSettingRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.ValidationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;
    private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final BaiduCensorService baiduCensorService;
    private final SiteSettingRepository siteSettingRepository;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            AppProperties appProperties,
            BaiduCensorService baiduCensorService,
            SiteSettingRepository siteSettingRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
        this.baiduCensorService = baiduCensorService;
        this.siteSettingRepository = siteSettingRepository;
    }

    @Transactional
    public void register(String username, String email, String password, String confirmPassword) {
        String normalizedUsername = normalize(username);
        String normalizedEmail = normalize(email).toLowerCase();

        if (normalizedUsername.isBlank()) {
            throw new ValidationException("请输入用户名");
        }
        if (!normalizedEmail.matches("^\\S+@\\S+\\.\\S+$")) {
            throw new ValidationException("请输入有效的邮箱地址");
        }
        if (password == null || password.length() < 6) {
            throw new ValidationException("密码长度至少为 6 位");
        }
        if (!password.equals(confirmPassword)) {
            throw new ValidationException("两次输入的密码不一致");
        }
        if (userAccountRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new ValidationException("用户名已存在");
        }
        if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ValidationException("邮箱已被注册");
        }

        if (!baiduCensorService.isTextValid(normalizedUsername)) {
            throw new ValidationException("用户名包含违规或敏感词汇");
        }

        UserAccount account = new UserAccount();
        account.setUsername(normalizedUsername);
        account.setEmail(normalizedEmail);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setRole(UserRole.USER);
        userAccountRepository.save(account);
    }

    public Optional<UserAccount> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return Optional.empty();
        }
        return userAccountRepository.findByEmailIgnoreCase(authentication.getName());
    }

    public UserAccount getRequiredCurrentUser() {
        return getCurrentUser().orElseThrow(() -> new ValidationException("当前未登录"));
    }

    @Transactional
    public UserAccount updateProfile(String username, String avatarUrl, MultipartFile avatarFile) {
        UserAccount currentUser = getRequiredCurrentUser();
        String normalizedUsername = normalize(username);

        // Check daily update limit
        java.time.LocalDate today = java.time.LocalDate.now();
        int dailyLimit = getProfileUpdateDailyLimit();
        
        if (currentUser.getLastProfileUpdateDate() == null || !currentUser.getLastProfileUpdateDate().equals(today)) {
            currentUser.setLastProfileUpdateDate(today);
            currentUser.setDailyProfileUpdateCount(0);
        }

        if (currentUser.getDailyProfileUpdateCount() >= dailyLimit) {
            throw new ValidationException("今日修改资料次数已达上限 (" + dailyLimit + "次)");
        }

        if (normalizedUsername.isBlank()) {
            throw new ValidationException("请输入用户名");
        }
        boolean usernameTaken = userAccountRepository.findByUsernameIgnoreCase(normalizedUsername)
                .filter(existing -> !existing.getId().equals(currentUser.getId()))
                .isPresent();
        if (usernameTaken) {
            throw new ValidationException("用户名已存在");
        }

        if (!currentUser.getUsername().equals(normalizedUsername) && !baiduCensorService.isTextValid(normalizedUsername)) {
            throw new ValidationException("用户名包含违规或敏感词汇");
        }

        String nextAvatarUrl = normalizeAvatarUrl(avatarUrl);
        if (avatarFile != null && !avatarFile.isEmpty()) {
            nextAvatarUrl = storeAvatar(currentUser, avatarFile);
        } else if (nextAvatarUrl.isBlank() && currentUser.getAvatarUrl() != null) {
            // 如果表单传来的外链为空，且没有上传新文件，保留原有头像
            nextAvatarUrl = currentUser.getAvatarUrl();
        } else if (!nextAvatarUrl.isBlank() && currentUser.getAvatarUrl() != null && !nextAvatarUrl.equals(currentUser.getAvatarUrl())) {
            // 切换为外部链接，删除旧的本地头像
            deleteManagedAvatar(currentUser.getAvatarUrl());
        }

        currentUser.setUsername(normalizedUsername);
        currentUser.setAvatarUrl(nextAvatarUrl.isBlank() ? null : nextAvatarUrl);
        currentUser.setDailyProfileUpdateCount(currentUser.getDailyProfileUpdateCount() + 1);
        return userAccountRepository.save(currentUser);
    }

    public int getProfileUpdateDailyLimit() {
        return siteSettingRepository.findById("profile_update_daily_limit")
                .map(setting -> {
                    try {
                        return Integer.parseInt(setting.getValue());
                    } catch (NumberFormatException e) {
                        return 3;
                    }
                })
                .orElse(3);
    }

    @Transactional
    public UserAccount findOrCreateSwuUser(SwuIdmAuthService.AuthenticatedCampusUser campusUser) {
        String userId = normalize(campusUser.userId());
        if (userId.isBlank()) {
            throw new ValidationException("统一认证用户标识不能为空");
        }
        String syntheticEmail = buildSyntheticEmail(userId);
        return userAccountRepository.findByEmailIgnoreCase(syntheticEmail)
                .orElseGet(() -> {
                    UserAccount account = new UserAccount();
                    account.setUsername(userId);
                    account.setEmail(syntheticEmail);
                    account.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
                    account.setRole(UserRole.USER);
                    return userAccountRepository.save(account);
                });
    }

    public void loginSwuUser(HttpServletRequest request, SwuIdmAuthService.AuthenticatedCampusUser campusUser) {
        UserAccount account = findOrCreateSwuUser(campusUser);
        var principal = User.withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .authorities(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))
                .build();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeAvatarUrl(String avatarUrl) {
        String normalized = normalize(avatarUrl);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.startsWith("/uploads/avatars/")) {
            return normalized;
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        throw new ValidationException("请输入有效的头像链接");
    }

    private String storeAvatar(UserAccount userAccount, MultipartFile avatarFile) {
        validateAvatarFile(avatarFile);

        String extension = resolveExtension(avatarFile);
        Path avatarDirectory = Path.of(appProperties.getUploadDir())
                .toAbsolutePath()
                .normalize()
                .resolve("avatars")
                .resolve(String.valueOf(userAccount.getId()));
        String fileName = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetFile = avatarDirectory.resolve(fileName);

        try {
            Files.createDirectories(avatarDirectory);
            try (InputStream inputStream = avatarFile.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ValidationException("头像上传失败，请稍后重试");
        }

        deleteManagedAvatar(userAccount.getAvatarUrl());
        return "/uploads/avatars/" + userAccount.getId() + "/" + fileName;
    }

    private void validateAvatarFile(MultipartFile avatarFile) {
        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new ValidationException("请先选择头像图片");
        }
        if (avatarFile.getSize() > MAX_AVATAR_SIZE) {
            throw new ValidationException("头像图片不能超过 5MB");
        }

        String contentType = normalize(avatarFile.getContentType()).toLowerCase();
        if (!contentType.isBlank() && !MediaType.IMAGE_JPEG_VALUE.equals(contentType)
                && !MediaType.IMAGE_PNG_VALUE.equals(contentType)
                && !MediaType.IMAGE_GIF_VALUE.equals(contentType)
                && !"image/webp".equals(contentType)) {
            throw new ValidationException("头像仅支持 jpg、jpeg、png、webp、gif 图片");
        }

        String extension = resolveExtension(avatarFile);
        if (!SUPPORTED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new ValidationException("头像仅支持 jpg、jpeg、png、webp、gif 图片");
        }

        try {
            validateImageContent(avatarFile, extension);
        } catch (IOException exception) {
            throw new ValidationException("头像图片读取失败");
        }
    }

    private void validateImageContent(MultipartFile avatarFile, String extension) throws IOException {
        byte[] imageBytes = avatarFile.getBytes();
        if ("webp".equals(extension)) {
            if (!isValidWebp(imageBytes)) {
                throw new ValidationException("上传文件不是有效图片");
            }
            return;
        }

        try (InputStream inputStream = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new ValidationException("上传文件不是有效图片");
            }
        }
    }

    private boolean isValidWebp(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length < 12) {
            return false;
        }
        String riff = new String(imageBytes, 0, 4, StandardCharsets.US_ASCII);
        String webp = new String(imageBytes, 8, 4, StandardCharsets.US_ASCII);
        return "RIFF".equals(riff) && "WEBP".equals(webp);
    }

    private String resolveExtension(MultipartFile file) {
        String originalFilename = normalize(file.getOriginalFilename());
        int dotIndex = originalFilename.lastIndexOf('.');
        String extension = dotIndex >= 0 ? originalFilename.substring(dotIndex + 1) : "jpg";
        return extension.toLowerCase();
    }

    private void deleteManagedAvatar(String avatarUrl) {
        String normalized = normalize(avatarUrl);
        if (!normalized.startsWith("/uploads/avatars/")) {
            return;
        }

        String relativePath = normalized.replaceFirst("^/uploads/", "");
        Path avatarPath = Path.of(appProperties.getUploadDir()).toAbsolutePath().normalize().resolve(relativePath).normalize();

        try {
            Files.deleteIfExists(avatarPath);
        } catch (IOException ignored) {
        }
    }

    private String buildSyntheticEmail(String userId) {
        return userId.toLowerCase() + "@swu-sso.local";
    }
}
