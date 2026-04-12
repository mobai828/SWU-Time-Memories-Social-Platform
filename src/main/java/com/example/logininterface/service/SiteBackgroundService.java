package com.example.logininterface.service;

import com.example.logininterface.config.AppProperties;
import com.example.logininterface.domain.SiteSetting;
import com.example.logininterface.dto.LoginBackgroundConfig;
import com.example.logininterface.repository.SiteSettingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SiteBackgroundService {

    private static final int MIN_WIDTH = 1600;
    private static final int MIN_HEIGHT = 900;
    private static final double MIN_DETAIL_SCORE = 8.0;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final SiteSettingRepository siteSettingRepository;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public SiteBackgroundService(
            SiteSettingRepository siteSettingRepository,
            AppProperties appProperties,
            ObjectMapper objectMapper
    ) {
        this.siteSettingRepository = siteSettingRepository;
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    public LoginBackgroundConfig getConfig() {
        String legacyUrl = getSettingValue("login_bg_url").orElse(appProperties.getDefaultLoginBackground());
        return LoginBackgroundConfig.normalized(parseConfig(getSettingValue("login_bg_config").orElse(null), legacyUrl), legacyUrl);
    }

    public String resolveDisplayImage() {
        LoginBackgroundConfig config = getConfig();
        List<String> images = config.getImages();
        if (images.isEmpty()) {
            return appProperties.getDefaultLoginBackground();
        }
        if (!config.isRandomEnabled() || images.size() == 1) {
            return config.getSelectedImage();
        }
        return images.get(ThreadLocalRandom.current().nextInt(images.size()));
    }

    @Transactional
    public void updateSettings(String selectedImage, boolean randomEnabled) {
        LoginBackgroundConfig currentConfig = getConfig();
        LoginBackgroundConfig nextConfig = new LoginBackgroundConfig();
        nextConfig.setImages(currentConfig.getImages());
        nextConfig.setSelectedImage(selectedImage);
        nextConfig.setRandomEnabled(randomEnabled);
        saveConfig(nextConfig);
    }

    @Transactional
    public void addExternalBackground(String imageUrl) {
        if (!isValidUrl(imageUrl)) {
            throw new ValidationException("请输入有效的图片链接");
        }

        LoginBackgroundConfig currentConfig = getConfig();
        List<String> nextImages = new ArrayList<>(currentConfig.getImages());
        String normalized = imageUrl.trim();
        if (nextImages.contains(normalized)) {
            throw new ValidationException("这张背景图已存在");
        }
        nextImages.add(normalized);

        LoginBackgroundConfig nextConfig = new LoginBackgroundConfig();
        nextConfig.setImages(nextImages);
        nextConfig.setSelectedImage(normalized);
        nextConfig.setRandomEnabled(currentConfig.isRandomEnabled());
        saveConfig(nextConfig);
    }

    @Transactional
    public UploadSummary uploadBackgrounds(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new ValidationException("请先选择至少一张图片");
        }

        LoginBackgroundConfig currentConfig = getConfig();
        List<String> nextImages = new ArrayList<>(currentConfig.getImages());
        int addedCount = 0;
        int reusedCount = 0;
        int skippedCount = 0;
        List<String> messages = new ArrayList<>();
        String latestImage = currentConfig.getSelectedImage();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            try {
                UploadOutcome outcome = saveUpload(file);
                if (nextImages.contains(outcome.url())) {
                    skippedCount++;
                    messages.add(file.getOriginalFilename() + " 已存在，已跳过。");
                    continue;
                }

                if (outcome.reused()) {
                    reusedCount++;
                } else {
                    addedCount++;
                }

                nextImages.add(outcome.url());
                latestImage = outcome.url();
            } catch (ValidationException exception) {
                skippedCount++;
                messages.add(file.getOriginalFilename() + "：" + exception.getMessage());
            } catch (IOException exception) {
                skippedCount++;
                messages.add(file.getOriginalFilename() + "：上传失败。");
            }
        }

        LoginBackgroundConfig nextConfig = new LoginBackgroundConfig();
        nextConfig.setImages(nextImages);
        nextConfig.setSelectedImage(latestImage);
        nextConfig.setRandomEnabled(currentConfig.isRandomEnabled());
        saveConfig(nextConfig);
        return new UploadSummary(addedCount, reusedCount, skippedCount, messages);
    }

    @Transactional
    public void removeBackground(String imageUrl) {
        LoginBackgroundConfig currentConfig = getConfig();
        if (!currentConfig.getImages().contains(imageUrl)) {
            return;
        }

        List<String> nextImages = currentConfig.getImages().stream()
                .filter(image -> !image.equals(imageUrl))
                .toList();

        LoginBackgroundConfig nextConfig = new LoginBackgroundConfig();
        nextConfig.setImages(nextImages);
        nextConfig.setSelectedImage(currentConfig.getSelectedImage().equals(imageUrl) ? null : currentConfig.getSelectedImage());
        nextConfig.setRandomEnabled(currentConfig.isRandomEnabled());
        LoginBackgroundConfig normalizedNextConfig = saveConfig(nextConfig);

        if (!normalizedNextConfig.getImages().contains(imageUrl)) {
            deleteIfManagedFile(imageUrl);
        }
    }

    public Path getUploadRoot() {
        return Path.of(appProperties.getUploadDir()).toAbsolutePath().normalize();
    }

    private Optional<String> getSettingValue(String key) {
        return siteSettingRepository.findById(key).map(SiteSetting::getValue);
    }

    private LoginBackgroundConfig parseConfig(String rawValue, String legacyUrl) {
        if (rawValue == null || rawValue.isBlank()) {
            LoginBackgroundConfig config = new LoginBackgroundConfig();
            config.setImages(List.of(legacyUrl));
            config.setSelectedImage(legacyUrl);
            config.setRandomEnabled(false);
            return config;
        }

        try {
            LoginBackgroundConfig parsed = objectMapper.readValue(rawValue, LoginBackgroundConfig.class);
            return LoginBackgroundConfig.normalized(parsed, legacyUrl);
        } catch (JsonProcessingException exception) {
            if (isValidUrl(rawValue)) {
                LoginBackgroundConfig fallback = new LoginBackgroundConfig();
                fallback.setImages(List.of(rawValue.trim()));
                fallback.setSelectedImage(rawValue.trim());
                return fallback;
            }
            LoginBackgroundConfig fallback = new LoginBackgroundConfig();
            fallback.setImages(List.of(legacyUrl));
            fallback.setSelectedImage(legacyUrl);
            return fallback;
        }
    }

    private LoginBackgroundConfig saveConfig(LoginBackgroundConfig config) {
        LoginBackgroundConfig normalized = LoginBackgroundConfig.normalized(config, appProperties.getDefaultLoginBackground());
        saveSetting("login_bg_url", normalized.getSelectedImage());
        saveSetting("login_bg_config", writeJson(normalized));
        return normalized;
    }

    private String writeJson(LoginBackgroundConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("背景配置序列化失败", exception);
        }
    }

    private void saveSetting(String key, String value) {
        SiteSetting setting = siteSettingRepository.findById(key).orElseGet(SiteSetting::new);
        setting.setSettingKey(key);
        setting.setValue(value);
        siteSettingRepository.save(setting);
    }

    private UploadOutcome saveUpload(MultipartFile file) throws IOException {
        validateImage(file);
        String hash = sha256(file.getBytes());
        String extension = resolveExtension(file);
        Path uploadPath = getUploadRoot().resolve("login-backgrounds").resolve(hash + "." + extension);
        Files.createDirectories(uploadPath.getParent());
        boolean reused = Files.exists(uploadPath);

        if (!reused) {
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, uploadPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return new UploadOutcome("/uploads/login-backgrounds/" + uploadPath.getFileName(), reused);
    }

    private void validateImage(MultipartFile file) throws IOException {
        String extension = resolveExtension(file);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ValidationException("仅支持 jpg、jpeg、png、webp 图片");
        }

        if ("webp".equals(extension)) {
            byte[] header = new byte[12];
            try (InputStream inputStream = file.getInputStream()) {
                if (inputStream.read(header) != 12) {
                    throw new ValidationException("WebP 文件不完整");
                }
                String riff = new String(header, 0, 4);
                String webp = new String(header, 8, 4);
                if (!"RIFF".equals(riff) || !"WEBP".equals(webp)) {
                    throw new ValidationException("无效的 WebP 文件格式");
                }
            }
            return; // WebP skip detailed ImageIO checks as default environment might not support it
        }

        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new ValidationException("上传文件不是有效图片");
        }
        if (image.getWidth() < MIN_WIDTH || image.getHeight() < MIN_HEIGHT) {
            throw new ValidationException("背景图至少需要 1600×900 像素");
        }
        if (getDetailScore(image) < MIN_DETAIL_SCORE) {
            throw new ValidationException("图片细节偏少或清晰度不足");
        }
    }

    private double getDetailScore(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int maxSample = 240;
        double scale = Math.min(1.0, maxSample / (double) Math.max(width, height));
        int sampleWidth = Math.max(64, (int) Math.round(width * scale));
        int sampleHeight = Math.max(64, (int) Math.round(height * scale));
        BufferedImage sampled = new BufferedImage(sampleWidth, sampleHeight, BufferedImage.TYPE_INT_RGB);
        sampled.getGraphics().drawImage(image, 0, 0, sampleWidth, sampleHeight, null);

        double detailScore = 0;
        int sampleCount = 0;
        for (int y = 1; y < sampleHeight; y++) {
            for (int x = 1; x < sampleWidth; x++) {
                int current = sampled.getRGB(x, y);
                int left = sampled.getRGB(x - 1, y);
                int top = sampled.getRGB(x, y - 1);
                double currentGray = grayscale(current);
                double leftGray = grayscale(left);
                double topGray = grayscale(top);
                detailScore += Math.abs(currentGray - leftGray) + Math.abs(currentGray - topGray);
                sampleCount++;
            }
        }
        return sampleCount == 0 ? MIN_DETAIL_SCORE : detailScore / sampleCount;
    }

    private double grayscale(int rgb) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return red * 0.299 + green * 0.587 + blue * 0.114;
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境不支持 SHA-256", exception);
        }
    }

    private String resolveExtension(MultipartFile file) {
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("background.jpg");
        int index = originalName.lastIndexOf('.');
        String extension = index >= 0 ? originalName.substring(index + 1) : "jpg";
        return extension.toLowerCase(Locale.ROOT);
    }

    private boolean isValidUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (Exception exception) {
            return false;
        }
    }

    private void deleteIfManagedFile(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/login-backgrounds/")) {
            return;
        }

        try {
            Files.deleteIfExists(resolveUploadPath(imageUrl));
        } catch (IOException ignored) {
        }
    }

    private Path resolveUploadPath(String imageUrl) {
        String relativePath = imageUrl.replaceFirst("^/uploads/", "");
        return getUploadRoot().resolve(relativePath).normalize();
    }

    public record UploadSummary(int addedCount, int reusedCount, int skippedCount, List<String> messages) {
    }

    private record UploadOutcome(String url, boolean reused) {
    }
}
