package org.example.tradeGovernance;

import org.example.Main;
import org.example.dataAnalysis.DepthPacketStrategies;
import org.example.tradeGovernance.model.Order;
import org.example.tradeGovernance.model.Position;
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

    public Action evaluateTradeAction(Integer securityId, DepthPacketStrategies.Signal signal, String jwtToken) {
        List<Position> positions = Main.currentPositions;

        if (positions == null || positions.isEmpty()) {
            return signalToAction(signal);
        }

        for (Position position : positions) {
            if (position.getSecurity_id().equals(securityId)) {
                int netQty = position.getNet_qty();

                if (signal == DepthPacketStrategies.Signal.BUY) {
                    if (netQty < 0) return Action.EXIT;
                    if (netQty == 0) return Action.BUY;
                    return Action.HOLD;
                }

                if (signal == DepthPacketStrategies.Signal.SELL) {
                    if (netQty > 0) return Action.EXIT;
                    if (netQty == 0) return Action.SELL;
                    return Action.HOLD;
                }

                return Action.HOLD;
            }
        }

        return signalToAction(signal);
    }

    private Action signalToAction(DepthPacketStrategies.Signal signal) {
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
            double targetPercent
    ) {
        System.out.println("🔍 Evaluating open positions for PnL...");

        for (Position position : positions) {
            /**
             * 🚫 Skip closed positions:
             * 'net_qty' represents the net open quantity of the position.
             * - A value of 0 indicates that there is no open position (fully squared off).
             * - Therefore, we only need to evaluate positions where some quantity is still open
             *   (i.e., 'net_qty' is either positive for long or negative for short positions).
             */
            if (position.getNet_qty() == 0) continue;

            String securityId = position.getSecurity_id();
            double ltp = position.getLast_traded_price();
            int netQty = position.getNet_qty();


            // 🧾 Find most recent *completed* order for this security to get avg traded price
            List<org.example.tradeGovernance.model.Order> completedOrders = orders.stream()
                    .filter(order -> order.getSecurityId().equals(securityId))
                    .filter(order -> {
                        String status = order.getDisplayStatus();
                        return status != null &&
                                (status.equalsIgnoreCase("Completed") || status.equalsIgnoreCase("Successful"));
                    })
                    .sorted(Comparator.comparing(Order::getOrderDateTime).reversed()) // Latest first
                    .collect(Collectors.toList());

            // 🚫 If no relevant completed order found, skip evaluation
            if (completedOrders.isEmpty()) {
                System.out.println("⚠️ No completed order found for " + securityId + " → Skipping...");
                continue;
            }

            org.example.tradeGovernance.model.Order latestCompletedOrder = completedOrders.get(0);
            double avgTradedPrice = latestCompletedOrder.getAvgTradedPrice();

            // 📈 Define dynamic target/SL price ranges based on trade price
            double upperLimit = avgTradedPrice * (1 + (targetPercent / 100));
            double lowerLimit = avgTradedPrice * (1 - (stopLossPercent / 100));

            System.out.println("📘 " + securityId + " | Position: " + netQty +
                    " | AvgPrice: " + avgTradedPrice + " | LTP: " + ltp);

            // 📊 Check price thresholds based on long or short position
            if (netQty > 0) { // 🟢 Long Position
                if (ltp >= upperLimit) {
                    System.out.println("🎯 Target reached for " + securityId);
                } else if (ltp <= lowerLimit) {
                    System.out.println("🛑 Stop-loss hit for " + securityId);
                } else {
                    System.out.println("📊 Within range. Monitoring continues.");
                }
            } else { // 🔴 Short Position
                if (ltp <= lowerLimit) {
                    System.out.println("🎯 Target reached for short " + securityId);
                } else if (ltp >= upperLimit) {
                    System.out.println("🛑 Stop-loss hit for short " + securityId);
                } else {
                    System.out.println("📊 Within range for short. Monitoring continues.");
                }
            }
        }
    }



}

