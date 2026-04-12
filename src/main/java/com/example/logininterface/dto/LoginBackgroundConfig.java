package com.example.logininterface.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LoginBackgroundConfig {

    private List<String> images = new ArrayList<>();
    private boolean randomEnabled;
    private String selectedImage;

    public static LoginBackgroundConfig normalized(LoginBackgroundConfig source, String fallbackImage) {
        LoginBackgroundConfig normalized = new LoginBackgroundConfig();
        Set<String> uniqueImages = new LinkedHashSet<>();

        if (source != null && source.getImages() != null) {
            for (String image : source.getImages()) {
                if (image != null && !image.isBlank()) {
                    uniqueImages.add(image.trim());
                }
            }
        }

        normalized.images = new ArrayList<>(uniqueImages);
        String selectedImage = source != null ? source.getSelectedImage() : null;
        if (selectedImage != null) {
            selectedImage = selectedImage.trim();
        }

        if (!normalized.images.isEmpty()) {
            normalized.selectedImage = normalized.images.contains(selectedImage)
                    ? selectedImage
                    : normalized.images.getFirst();
        } else {
            normalized.selectedImage = fallbackImage != null ? fallbackImage.trim() : "";
        }
        normalized.randomEnabled = source != null && source.isRandomEnabled() && normalized.images.size() > 1;
        return normalized;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }

    public boolean isRandomEnabled() {
        return randomEnabled;
    }

    public void setRandomEnabled(boolean randomEnabled) {
        this.randomEnabled = randomEnabled;
    }

    public String getSelectedImage() {
        return selectedImage;
    }

    public void setSelectedImage(String selectedImage) {
        this.selectedImage = selectedImage;
    }
}
