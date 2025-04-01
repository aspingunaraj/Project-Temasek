package org.example.tradeGovernance;

import org.example.Main;
import org.example.tradeGovernance.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates a bracket order using regular (market, limit, SL-M) orders.
 * Monitors live order book to detect target or stop-loss execution and cancels the counter order accordingly.
 */
public class BracketOrderManager {

    private final OrderServices orderServices = new OrderServices();

    // Percent values to compute profit and SL prices
    private final double targetPercent;
    private final double stopLossPercent;

    // Track running monitors for each symbol to avoid duplicates
    private final Map<String, Timer> activeMonitors = new ConcurrentHashMap<>();

    public BracketOrderManager(double targetPercent, double stopLossPercent) {
        this.targetPercent = targetPercent;
        this.stopLossPercent = stopLossPercent;
    }

    /**
     * Main method to simulate a bracket order using 3 regular orders:
     * - Market Entry
     * - Limit Target
     * - SL-M Stop Loss
     */
    public void placeBracketOrder(String securityId, TradeAnalysis.Action actionType) {
        double ltp = simulateLTP(securityId);
        String txnType = (actionType == TradeAnalysis.Action.BUY) ? "B" : "S";

        // 🔹 1️⃣ Entry Market Order (No price or trigger required)
        NormalOrderRequest entryOrder = new NormalOrderRequest(
                txnType, "NSE", "E", "I", securityId, 1,
                "DAY", "MKT", 0.0, null, "N", "false"
        );
        orderServices.placeNormalOrder(entryOrder);

        System.out.println("targetPecent" + targetPercent);
        // 🔹 2️⃣ Compute Target and SL prices
        double targetPrice = (actionType == TradeAnalysis.Action.BUY)
                ? ltp * (1 + (targetPercent / 100))
                : ltp * (1 - (targetPercent / 100));




        double stopLossPrice = (actionType == TradeAnalysis.Action.BUY)
                ? ltp * (1 - (stopLossPercent / 100))
                : ltp * (1 + (stopLossPercent / 100));

        System.out.println("ltp" + ltp);
        System.out.println("targetPrice" + targetPrice);
        System.out.println("stopLoss" + stopLossPrice);

        // 🔹 3️⃣ Place Target Limit Order (No trigger price)
        NormalOrderRequest targetOrder = new NormalOrderRequest(
                (actionType == TradeAnalysis.Action.BUY) ? "S" : "B", "NSE", "E", "I",
                securityId, 1, "DAY", "LMT", targetPrice, null, "N", "false"
        );

        // 🔹 4️⃣ Place SL-M Stop Loss Order (Requires triggerPrice)
        NormalOrderRequest stopLossOrder = new NormalOrderRequest(
                (actionType == TradeAnalysis.Action.BUY) ? "S" : "B", "NSE", "E", "I",
                securityId, 1, "DAY", "SLM", 0.0, stopLossPrice, "N", "false"
        );

        orderServices.placeNormalOrder(targetOrder);
        orderServices.placeNormalOrder(stopLossOrder);

        // 🔄 5️⃣ Start Monitoring to detect if either order is executed
        monitorAndCancelCounterExit(securityId, actionType);
    }

    /**
     * Monitors the order book every second to check if either the target or stop-loss order is filled.
     * If one is filled, the other is cancelled.
     */
    private void monitorAndCancelCounterExit(String securityId, TradeAnalysis.Action action) {
        if (activeMonitors.containsKey(securityId)) {
            System.out.println("⚠️ Monitor already running for " + securityId);
            return;
        }

        Timer timer = new Timer();
        activeMonitors.put(securityId, timer);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                OrderBookResponse orderBook = Main.latestOrderBook;

                if (orderBook == null || orderBook.getData() == null) return;

                // Identify relevant orders
                Order targetOrder = null;
                Order stopLossOrder = null;

                for (Order order : orderBook.getData()) {
                    if (!order.getSecurityId().equals(securityId)) continue;

                    if (order.getOrderType().equals("LMT") && order.getStatus().startsWith("O")) {
                        targetOrder = order;
                    } else if (order.getOrderType().equals("SLM") && order.getStatus().startsWith("O")) {
                        stopLossOrder = order;
                    }
                }

                // Check if either order is missing from open list — means it is executed
                boolean targetExecuted = (targetOrder == null);
                boolean slExecuted = (stopLossOrder == null);

                if (targetExecuted && !slExecuted) {
                    System.out.println("🎯 Target executed for " + securityId + ". Cancelling SLM order.");
                    cancelMatchingOrder(securityId, "SLM", orderBook);
                    stopMonitor(securityId, timer);
                } else if (slExecuted && !targetExecuted) {
                    System.out.println("🛑 Stop-Loss executed for " + securityId + ". Cancelling LMT order.");
                    cancelMatchingOrder(securityId, "LMT", orderBook);
                    stopMonitor(securityId, timer);
                }
            }
        }, 0, 1000);
    }

    /**
     * Cancels an open order of given type (SL-M or LMT) for the securityId.
     */
    private void cancelMatchingOrder(String securityId, String orderType, OrderBookResponse orderBook) {
        for (Order order : orderBook.getData()) {
            if (order.getSecurityId().equals(securityId)
                    && order.getOrderType().equals(orderType)
                    && order.getStatus().startsWith("O")) {

                OrderCancelRequest cancelRequest = new OrderCancelRequest(
                        order.getOrderNo(),
                        order.getSerialNo(),
                        order.getGroupId()
                );

                orderServices.cancelNormalOrder(cancelRequest);
                break;
            }
        }
    }

    /**
     * Clean up timer once bracket is resolved.
     */
    private void stopMonitor(String securityId, Timer timer) {
        timer.cancel();
        activeMonitors.remove(securityId);
        System.out.println("🛑 Monitor stopped for " + securityId);
    }

    /**
     * Simulated LTP fetch — replace with live feed or market data APIs.
     */
    private double simulateLTP(String securityId) {
        return 100 + Math.random() * 10;
    }
}
