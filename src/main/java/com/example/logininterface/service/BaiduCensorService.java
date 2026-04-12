package com.example.logininterface.service;

import com.example.logininterface.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ValidationException;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class BaiduCensorService {

    private final AppProperties appProperties;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private String cachedAccessToken;
    private Instant tokenExpiryTime;

    public BaiduCensorService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Checks if the given text is valid according to Baidu Content Moderation API.
     *
     * @param text The text to moderate
     * @return true if the text is valid (compliant), false if non-compliant or suspected.
     */
    public boolean isTextValid(String text) {
        AppProperties.BaiduCensor config = appProperties.getBaiduCensor();
        if (config == null || config.getApiKey() == null || config.getSecretKey() == null) {
            // If not configured, we let it pass
            return true;
        }
        
        if (text == null || text.trim().isEmpty()) {
            return true;
        }

        try {
            String accessToken = getAccessToken();
            String url = "https://aip.baidubce.com/rest/2.0/solution/v1/text_censor/v2/user_defined?access_token=" + accessToken;

            RequestBody body = new FormBody.Builder()
                    .add("text", text)
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return false; // Or throw exception? Let's just return false on API failure to be safe
                }

                String responseBody = response.body().string();
                JsonNode rootNode = objectMapper.readTree(responseBody);
                
                if (rootNode.has("error_code")) {
                    return false; // API returned an error
                }

                if (rootNode.has("conclusionType")) {
                    int conclusionType = rootNode.get("conclusionType").asInt();
                    // 1: 合规, 2: 不合规, 3: 疑似, 4: 审核失败
                    return conclusionType == 1;
                }
                
                return true;
            }

        } catch (Exception e) {
            // If the service is down or network error occurs, we throw an exception so the user is informed
            throw new jakarta.validation.ValidationException("内容审核服务异常，请稍后重试");
        }
    }

    private synchronized String getAccessToken() throws IOException {
        if (cachedAccessToken != null && tokenExpiryTime != null && Instant.now().isBefore(tokenExpiryTime)) {
            return cachedAccessToken;
        }

        AppProperties.BaiduCensor config = appProperties.getBaiduCensor();
        String url = "https://aip.baidubce.com/oauth/2.0/token" +
                "?grant_type=client_credentials" +
                "&client_id=" + config.getApiKey() +
                "&client_secret=" + config.getSecretKey();

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(new byte[0]))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to get access token: " + response.code());
            }

            String responseBody = response.body().string();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            
            if (rootNode.has("access_token")) {
                this.cachedAccessToken = rootNode.get("access_token").asText();
                long expiresIn = rootNode.has("expires_in") ? rootNode.get("expires_in").asLong() : 2592000;
                this.tokenExpiryTime = Instant.now().plusSeconds(expiresIn - 60); // Refresh 1 minute early
                return this.cachedAccessToken;
            } else {
                throw new IOException("Access token not found in response");
            }
        }
    }
}
