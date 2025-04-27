package org.example.tradeGovernance;

import org.example.Main;
import org.example.dataAnalysis.depthStrategy.StrategyOne;
import org.example.tradeGovernance.model.Order;
import org.example.tradeGovernance.model.Position;
import org.example.websocket.model.Tick;

import java.util.Comparator;


import java.util.List;
import java.util.stream.Collectors;

public class TradeAnalysis {

    private final PositionServices positionServices;

    public TradeAnalysis() {
        this.positionServices = new PositionServices(); // You must define this class separately
    }

    public enum Action {
        BUY,   // Take a fresh buy position
        SELL,  // Take a fresh sell position
        EXIT,  // Close an existing position
        HOLD   // Maintain current state
    }

    public Action evaluateTradeAction(Integer securityId, StrategyOne.Signal signal, String jwtToken) {
        List<Position> positions = Main.currentPositions;

        if (positions == null || positions.isEmpty()) {
            return signalToAction(signal);
        }

        for (Position position : positions) {
            if (position.getSecurity_id().equals(securityId)) {
                int netQty = position.getNet_qty();

                if (signal == StrategyOne.Signal.BUY) {
                    if (netQty < 0) return Action.EXIT;
                    if (netQty == 0) return Action.BUY;
                    return Action.HOLD;
                }

                if (signal == StrategyOne.Signal.SELL) {
                    if (netQty > 0) return Action.EXIT;
                    if (netQty == 0) return Action.SELL;
                    return Action.HOLD;
                }

                return Action.HOLD;
            }
        }

        return signalToAction(signal);
    }

    private Action signalToAction(StrategyOne.Signal signal) {
        return switch (signal) {
            case BUY -> Action.BUY;
            case SELL -> Action.SELL;
            case HOLD -> Action.HOLD;
        };
    }

    /**
     * If the action is BUY or SELL, create and place a bracket order
     * by delegating to the external OrderExecutionService.
     */

    /**
     * Evaluates all open positions to determine if the current market price (LTP)
     * has breached either the configured stop-loss or target thresholds.
     * <p>
     * This can be used to monitor live trades and trigger alerts or exits if price limits are hit.
     *
     * @param positions       List of currently held positions
     * @param orders          List of historical and current orders (from order book)
     * @param stopLossPercent Percentage threshold for stop-loss breach (e.g. 0.5 for 0.5%)
     * @param targetPercent   Percentage threshold for target breach (e.g. 0.5 for 0.5%)
     */
    public static void evaluatePnLForOpenPositions(
            List<Position> positions,
            List<Order> orders,
            double stopLossPercent,
            double targetPercent,
            Tick tick
    ) {
        System.out.println("🔍 Evaluating PnL for Tick SecurityId: " + tick.getSecurityId());

        OrderServices orderServices = new OrderServices();
        String tickSecurityId = String.valueOf(tick.getSecurityId()); // Tick security ID
        double ltp = tick.getLastTradedPrice();

        // 🔍 Find the matching open position for the tick's security ID
        Position matchingPosition = positions.stream()
                .filter(position -> position.getSecurity_id().equals(tickSecurityId))
                .filter(position -> position.getNet_qty() != 0) // Only open positions
                .filter(position -> "I".equalsIgnoreCase(position.getProduct())) // Only Intraday positions
                .findFirst()
                .orElse(null);

        if (matchingPosition == null) {
            System.out.println("⚠️ No open position found for SecurityId: " + tickSecurityId);
            return;
        }

        int netQty = matchingPosition.getNet_qty();

        // ❌ Skip if there's already a pending order for this symbol
        if (orderServices.hasPendingOrderForSymbol(tickSecurityId)) {
            System.out.println("⏳ Pending order exists for " + tickSecurityId + ". Skipping PnL exit evaluation.");
            return;
        }

        // 🧾 Filter completed orders for this securityId
        List<Order> completedOrders = orders.stream()
                .filter(order -> order.getSecurityId().equals(tickSecurityId))
                .filter(order -> {
                    String status = order.getDisplayStatus();
                    return status != null &&
                            (status.equalsIgnoreCase("Completed") || status.equalsIgnoreCase("Successful"));
                })
                .sorted(Comparator.comparing(Order::getOrderDateTime).reversed()) // Latest first
                .collect(Collectors.toList());

        if (completedOrders.isEmpty()) {
            System.out.println("⚠️ No completed orders found for SecurityId: " + tickSecurityId);
            return;
        }

        Order latestCompletedOrder = completedOrders.get(0);
        double avgTradedPrice = latestCompletedOrder.getAvgTradedPrice();

        // 📈 Compute price thresholds
        double upperLimit = avgTradedPrice * (1 + (targetPercent / 100));
        double lowerLimit = avgTradedPrice * (1 - (stopLossPercent / 100));

        System.out.printf("📘 %s | Position: %d | AvgPrice: %.2f | LTP: %.2f | Target: %.2f | SL: %.2f%n",
                tickSecurityId, netQty, avgTradedPrice, ltp, upperLimit, lowerLimit);

        // 🟢 Long Position
        if (netQty > 0) {
            if (ltp >= upperLimit) {
                System.out.println("🎯 Target reached for " + tickSecurityId + ". Placing exit SELL order.");
                orderServices.placeExitOrder(tickSecurityId, "S", netQty);
            } else if (ltp <= lowerLimit) {
                System.out.println("🛑 Stop-loss hit for " + tickSecurityId + ". Placing exit SELL order.");
                orderServices.placeExitOrder(tickSecurityId, "S", netQty);
            } else {
                System.out.println("📊 Within range for long. Monitoring continues.");
            }
        }
        // 🔴 Short Position
        else {
            if (ltp <= lowerLimit) {
                System.out.println("🎯 Target reached for short " + tickSecurityId + ". Placing exit BUY order.");
                orderServices.placeExitOrder(tickSecurityId, "B", Math.abs(netQty));
            } else if (ltp >= upperLimit) {
                System.out.println("🛑 Stop-loss hit for short " + tickSecurityId + ". Placing exit BUY order.");
                orderServices.placeExitOrder(tickSecurityId, "B", Math.abs(netQty));
            } else {
                System.out.println("📊 Within range for short. Monitoring continues.");
            }
        }
    }



    /**
     * 📤 Squares off all open positions by placing reverse market orders.
     * This is typically used for EOD square-off or during emergency shutdown.
     */
    public void squareOffAllOpenPositions() {
        System.out.println("🔁 Initiating square-off for all open positions...");

        List<Position> positions = Main.currentPositions;
        OrderServices orderServices = new OrderServices();

        for (Position position : positions) {
            int netQty = position.getNet_qty();
            String securityId = position.getSecurity_id();

            // ❌ Skip closed positions
            if (netQty == 0) continue;

            // ⏳ Skip if there is a pending order for this security
            if (orderServices.hasPendingOrderForSymbol(securityId)) {
                System.out.println("⏳ Skipping " + securityId + " as pending order exists.");
                continue;
            }

            // 🧾 Prepare and place reverse exit order
            String reverseTxnType = (netQty > 0) ? "S" : "B";
            boolean exitPlaced = orderServices.placeExitOrder(securityId, reverseTxnType, Math.abs(netQty));

            if (exitPlaced) {
                System.out.println("✅ Square-off order placed for " + securityId);
            } else {
                System.err.println("❌ Failed to place square-off order for " + securityId);
            }
        }

        System.out.println("✅ Square-off evaluation completed.");
    }




}

