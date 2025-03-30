package org.example.tokenStorage;


import java.time.LocalDateTime;

public class TokenInfo {
    private String accessToken;
    private String publicAccessToken;
    private String readAccessToken;
    private LocalDateTime storedAt;

    // Getters and setters
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getPublicAccessToken() { return publicAccessToken; }
    public void setPublicAccessToken(String publicAccessToken) { this.publicAccessToken = publicAccessToken; }

    public String getReadAccessToken() { return readAccessToken; }
    public void setReadAccessToken(String readAccessToken) { this.readAccessToken = readAccessToken; }

    public LocalDateTime getStoredAt() { return storedAt; }
    public void setStoredAt(LocalDateTime storedAt) { this.storedAt = storedAt; }
}

