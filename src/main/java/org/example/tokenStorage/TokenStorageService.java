package org.example.tokenStorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.time.LocalDateTime;

public class TokenStorageService {

    private static final String TOKEN_FILE_PATH = "tokens.json";
    private final ObjectMapper objectMapper;

    public TokenStorageService() {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void saveTokens(TokenInfo tokenInfo) {
        try {
            tokenInfo.setStoredAt(LocalDateTime.now());
            objectMapper.writeValue(new File(TOKEN_FILE_PATH), tokenInfo);
            System.out.println("💾 Tokens saved to file.");
        } catch (Exception e) {
            System.err.println("❌ Failed to save tokens: " + e.getMessage());
        }
    }

    public TokenInfo loadTokens() {
        try {
            File file = new File(TOKEN_FILE_PATH);
            if (file.exists()) {
                TokenInfo tokens = objectMapper.readValue(file, TokenInfo.class);
                System.out.println("🔄 Loaded tokens from file. Stored at: " + tokens.getStoredAt());
                return tokens;
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to load tokens: " + e.getMessage());
        }
        return null;
    }

    public boolean isTokenExpired() {
        TokenInfo tokenInfo = loadTokens();
        if (tokenInfo == null) return true;

        // Tokens expire every day at midnight
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime stored = tokenInfo.getStoredAt();
        return !stored.toLocalDate().isEqual(now.toLocalDate());
    }
}

