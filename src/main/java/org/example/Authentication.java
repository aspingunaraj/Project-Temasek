package org.example;

import org.example.dataAnalysis.StrategyManager;
import org.example.dataAnalysis.StrategyOne;
import org.example.dataAnalysis.machineLearning.TickAdapter;
import org.example.dataAnalysis.machineLearning.TrainingDataWriter;
import org.example.tokenStorage.TokenInfo;
import org.example.tokenStorage.TokenStorageService;
import org.example.tradeGovernance.OrderServices;
import org.example.tradeGovernance.TradeAnalysis;
import org.example.websocket.WebSocketClient;
import org.example.websocket.dataPreparation.DepthPacketHistoryManager;
import org.example.websocket.dataPreparation.SubscriptionPreferenceBuilder;
import org.example.websocket.model.PreferenceDto;
import org.example.websocket.model.Tick;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class Authentication {

    private final Map<Integer, Long> lastEvaluated = new ConcurrentHashMap<>();
    private static final long EVALUATION_COOLDOWN_MS = 2000;

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
    public String tokenPage(@RequestParam("requestToken") String requestToken,
                            @RequestParam(value = "success", required = false) String success,
                            @RequestParam(value = "state", required = false) String state,
                            Model model) {
        System.out.println("Token received: " + requestToken);
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
        TokenStorageService tokenStorageService = new TokenStorageService();
        boolean expired = tokenStorageService.isTokenExpired();
        Map<String, Object> response = new HashMap<>();
        response.put("expired", expired);
        response.put("message", expired ? "🔐 Token expired. Please regenerate." : "✅ Token is valid.");
        return response;
    }

    public void generateAccessTokens(String apiKey, String apiSecret, String requestToken) {
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

                System.out.println("✅ Access Token: " + Main.accessToken);
                System.out.println("✅ Public Access Token: " + Main.publicAccessToken);
                System.out.println("✅ Read Access Token: " + Main.readAccessToken);

                initializeWebSocketConnection();
            } else {
                System.err.println("❌ Failed to get access tokens. Status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.err.println("❌ Exception occurred while generating tokens: " + e.getMessage());
        }
    }

    @GetMapping("/start-websocket")
    @ResponseBody
    public String startWebSocketManually() {
        try {
            initializeWebSocketConnection();
            return "✅ WebSocket initialization triggered.";
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    private void initializeWebSocketConnection() {
        try {
            WebSocketClient webSocketClient = new WebSocketClient(Main.publicAccessToken);

            webSocketClient.setOnOpenListener(() -> System.out.println("📡 WebSocket connected."));
            webSocketClient.setOnCloseListener(reason -> System.out.println("🔌 WebSocket closed: " + reason));

            webSocketClient.setOnErrorListener(new org.example.websocket.listeners.OnErrorListener() {
                public void onError(String errorMessage) { System.err.println("WebSocket Error: " + errorMessage); }
                public void onError(Exception e) { e.printStackTrace(); }
                public void onError(RuntimeException re) { re.printStackTrace(); }
            });

            DepthPacketHistoryManager historyManager = DepthPacketHistoryManager.getInstance();

            webSocketClient.setOnMessageListener(ticks -> {
                System.out.println("📈 Received " + ticks.size() + " ticks");

                for (Tick tick : ticks) {
                    int securityId = tick.getSecurityId();

                    if (tick.getMbpRowPacket() != null && !tick.getMbpRowPacket().isEmpty()) {
                        historyManager.addTick(tick, () -> checkAndTrainModelIfReady(securityId));
                    }

                    long now = System.currentTimeMillis();
                    if (lastEvaluated.containsKey(securityId) &&
                            now - lastEvaluated.get(securityId) < EVALUATION_COOLDOWN_MS) {
                        System.out.println("⏳ Skipping re-evaluation for " + securityId);
                        continue;
                    }

                    lastEvaluated.put(securityId, now);

                    StrategyOne.Signal signal = StrategyManager.strategySelector(
                            historyManager.getTickHistory(securityId), securityId);
                    System.out.println("🔍 Decision for Security ID " + securityId + ": " + signal);

                    TradeAnalysis tradeAnalysis = new TradeAnalysis();
                    TradeAnalysis.Action action = tradeAnalysis.evaluateTradeAction(securityId, signal, Main.accessToken);
                    System.out.println("🚦 Trade Action: " + action);

                    if (action == TradeAnalysis.Action.BUY || action == TradeAnalysis.Action.SELL) {
                        new OrderServices().orderManagement(String.valueOf(securityId), action, tick.getLastTradedPrice());
                    }
                }
            });

            webSocketClient.connect();
            Thread.sleep(1000);
            List<PreferenceDto> prefs = SubscriptionPreferenceBuilder.buildPreferences();
            webSocketClient.subscribe(prefs);

        } catch (Exception e) {
            System.err.println("❌ WebSocket Init Exception: " + e.getMessage());
        }
    }

    private void checkAndTrainModelIfReady(int securityId) {
        List<Tick> history = DepthPacketHistoryManager.getInstance().getTickHistory(securityId);

        if (history.size() >= 300) {
            System.out.println("🧠 300 ticks available in history for Security ID " + securityId + ". Preparing training examples...");

            try {
                List<TickAdapter.LabeledTick> labeledTicks = TickAdapter.labelTicks(history, 0.3);

                for (TickAdapter.LabeledTick labeled : labeledTicks) {
                    Map<String, Double> features = TickAdapter.extractFeatureMap(labeled.tick, history);
                    if (features != null) {
                        TrainingDataWriter.appendTrainingExample(new TrainingDataWriter.TrainingExample(features, labeled.label));
                    }
                }

                System.out.println("📦 Saved training data to JSON");

            } catch (Exception e) {
                System.err.println("❌ Failed during JSON append: " + e.getMessage());
            }
        }
    }

    @GetMapping("/strategy-stats")
    public String showStrategyStats(Model model) {
        model.addAttribute("strategyStats", StrategyOne.getStrategyStats());
        return "strategyStats"; // Thymeleaf template name
    }

}
