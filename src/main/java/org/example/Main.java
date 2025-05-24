package org.example;

import org.example.dataAnalysis.depthStrategy.StrategyOne;
import org.example.logger.WebSocketLogRedirector;
import org.example.tokenStorage.TokenInfo;
import org.example.tokenStorage.TokenStorageService;
import org.example.tradeGovernance.OrderServices;
import org.example.tradeGovernance.PositionServices;
import org.example.tradeGovernance.TradeAnalysis;
import org.example.tradeGovernance.model.OrderBookResponse;
import org.example.tradeGovernance.model.Position;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.PrintStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableScheduling // 🔁 Enables Spring Scheduler
public class Main {

    // 🔐 Global credentials and tokens
    public static String apiKey = "1a2231a035f44b5a828fdcc3757fdac2";
    public static String apiSecretKey = "7256b4240b1446598858722e58950cb3";
    public static String requestToken;
    public static String accessToken;
    public static String publicAccessToken;
    public static String readAccessToken;

    // 📦 Global cache for positions and order book
    public static final List<Position> currentPositions = new CopyOnWriteArrayList<>();
    public static volatile OrderBookResponse latestOrderBook;

    // 🔧 Services for data fetching
    private final PositionServices positionServices = new PositionServices();
    private final OrderServices orderServices = new OrderServices();

    public static void main(String[] args) {
        // 🔁 Redirect both standard output and error to WebSocket

        SpringApplication.run(Main.class, args);
        // Redirect logs to WebSocket and console
        System.setOut(new WebSocketLogRedirector());
        System.setErr(new WebSocketLogRedirector());

        System.out.println("🚀 Application Started");
        System.out.println("🚀 Backend started and log streaming is active");
        TokenInfo saved = new TokenStorageService().loadTokens();
        if (saved != null) {
            accessToken = saved.getAccessToken();
            publicAccessToken = saved.getPublicAccessToken();
            readAccessToken = saved.getReadAccessToken();
        }
    }

    /**
     * 🔁 Refreshes the global position list every 2 seconds.
     */
    @Scheduled(fixedDelay = 2000)
    public void updatePositions() {
        try {
            if (accessToken != null && !accessToken.isEmpty()) {
                List<Position> freshPositions = positionServices.getPositions(accessToken);
                if (freshPositions != null) {
                    currentPositions.clear();
                    currentPositions.addAll(freshPositions);

                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error updating positions: " + e.getMessage());
        }
    }

    /**
     * 📘 Refreshes the global order book every 2 seconds.
     */
    @Scheduled(fixedDelay = 2000)
    public void updateOrderBook() {
        try {
            if (accessToken != null && !accessToken.isEmpty()) {
                OrderBookResponse response = orderServices.getOrderBook(accessToken);
                if (response != null) {
                    latestOrderBook = response;
                    System.out.println("📘 Order book updated with " + response.getData().size() + " orders");
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error updating order book: " + e.getMessage());
        }
    }

    @Scheduled(cron = "0/30 0-14 9 * * *", zone = "Asia/Kolkata")
    public void scheduledSquareOffBetween230And245IST() {
        System.out.println("🛎️ Scheduled Square-Off Triggered (2:30–2:45 PM IST)");

        TradeAnalysis tradeAnalysis = new TradeAnalysis();
        tradeAnalysis.squareOffAllOpenPositions();
    }


    @Scheduled(fixedRate = 600000) // Check every 600 seconds
    public void checkMarketDataFileSizeAndTriggerRetraining() {
        try {
            Path filePath = Paths.get("src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/marketdata.jsonl.gz");

            if (Files.exists(filePath)) {
                long sizeInBytes = Files.size(filePath);
                long sizeInMB = sizeInBytes / (1024 * 1024);

                if (sizeInMB >= 48) {
                    System.out.println("📦 File size = " + sizeInMB + "MB. Triggering retraining...");

                    org.example.dataAnalysis.depthStrategy.machineLearning.trainingData.TrainingDataProcessor.triggerRetraining();
                    System.out.println("✅ Retraining successfully triggered due to file size threshold.");
                } else {
                    System.out.println("📏 File size = " + sizeInMB + "MB. Retraining not required.");
                }
            } else {
                System.out.println("⚠️ Market data file not found for size check.");
            }
        } catch (Exception e) {
            System.err.println("❌ Error while checking file size or triggering retraining: " + e.getMessage());
        }
    }





}