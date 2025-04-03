package org.example;

import org.example.logger.LogWebSocketHandler;
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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
                    System.out.println("📥 Position list updated: " + freshPositions.size() + " entries");
                    if (latestOrderBook != null && latestOrderBook.getData() != null) {
                        TradeAnalysis.evaluatePnLForOpenPositions(currentPositions, latestOrderBook.getData(),0.25,0.25);
                    }
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




}
