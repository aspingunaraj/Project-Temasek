package org.example.controller;

import org.example.Main;
import org.example.config.MarketModeConfig;
import org.example.tokenStorage.TokenInfo;
import org.example.tokenStorage.TokenStorageService;
import org.example.websocket.WebSocketService;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthenticationController {

    private final WebSocketService webSocketService;

    public AuthenticationController(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @GetMapping("/start-websocket")
    @ResponseBody
    public String startWebSocket(@RequestParam(defaultValue = "live") String mode) {
        MarketModeConfig.setSimulationMode("simulated".equalsIgnoreCase(mode));
        webSocketService.startWebSocket();  // ✅ This will now resolve
        return "WebSocket started in " + mode + " mode.";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("apiKey", Main.apiKey);
        model.addAttribute("apiSecretKey", Main.apiSecretKey);
        return "home";
    }

    @GetMapping("/tokens")
    @ResponseBody
    public Map<String, String> getTokens() {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", Main.accessToken);
        tokens.put("publicAccessToken", Main.publicAccessToken);
        tokens.put("readAccessToken", Main.readAccessToken);
        return tokens;
    }

    @GetMapping({"/token", "/token/"})
    public String tokenPage(@RequestParam("requestToken") String requestToken, Model model) {
        model.addAttribute("requestToken", requestToken);
        return "token";
    }

    @PostMapping("/save-token")
    public String saveToken(@RequestParam("token") String token, Model model) {
        Main.requestToken = token;
        generateAccessTokens(Main.apiKey, Main.apiSecretKey, Main.requestToken);
        model.addAttribute("message", "Token saved and access tokens generated!");
        model.addAttribute("requestToken", token);
        return "token";
    }

    @GetMapping("/token-status")
    @ResponseBody
    public Map<String, Object> getTokenStatus() {
        boolean expired = new TokenStorageService().isTokenExpired();
        Map<String, Object> response = new HashMap<>();
        response.put("expired", expired);
        response.put("message", expired ? "🔐 Token expired. Please regenerate." : "✅ Token is valid.");
        return response;
    }

    private void generateAccessTokens(String apiKey, String apiSecret, String requestToken) {
        try {
            String url = "https://developer.paytmmoney.com/accounts/v2/gettoken";
            RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = new HashMap<>();
            body.put("api_key", apiKey);
            body.put("api_secret_key", apiSecret);
            body.put("request_token", requestToken);

            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, String> responseBody = response.getBody();
                TokenInfo tokenInfo = new TokenInfo();
                tokenInfo.setAccessToken(responseBody.get("access_token"));
                tokenInfo.setPublicAccessToken(responseBody.get("public_access_token"));
                tokenInfo.setReadAccessToken(responseBody.get("read_access_token"));

                Main.accessToken = tokenInfo.getAccessToken();
                Main.publicAccessToken = tokenInfo.getPublicAccessToken();
                Main.readAccessToken = tokenInfo.getReadAccessToken();

                new TokenStorageService().saveTokens(tokenInfo);
            } else {
                System.err.println("❌ Failed to get access tokens. Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("❌ Exception during token generation: " + e.getMessage());
        }
    }
}
