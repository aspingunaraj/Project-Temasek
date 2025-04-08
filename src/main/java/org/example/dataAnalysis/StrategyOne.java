package org.example.dataAnalysis;

import org.example.websocket.model.DepthPacket;
import org.example.websocket.model.Tick;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class StrategyOne {

    public enum Signal {
        BUY, SELL, HOLD
    }

    private static final Map<String, EnumMap<Signal, AtomicInteger>> strategyStats = new HashMap<>();


    // ✅ Centralized Thresholds (easy to tweak)
    private static final double THRESHOLD_STRATEGY_1 = 1.3;
    private static final double THRESHOLD_STRATEGY_2 = 1.4;
    private static final double THRESHOLD_STRATEGY_3 = 1.5;
    private static final double THRESHOLD_STRATEGY_4_MULTIPLIER = 1.6;
    private static final double THRESHOLD_STRATEGY_5 = 1.2;
    private static final double THRESHOLD_STRATEGY_6_DROP = 0.6;
    private static final int    THRESHOLD_STRATEGY_6_COUNT = 3;
    private static final double THRESHOLD_STRATEGY_7_OFI = 12.0;
    private static final double THRESHOLD_STRATEGY_8 = 1.3;
    private static final int    MIN_VOTES_REQUIRED = 7;

    public static Signal evaluateMarketSignal(List<Tick> recentTicks) {
        int buyVotes = 0, sellVotes = 0, holdVotes = 0;

        Signal[] strategies = {
                strategy1(recentTicks),
                strategy2(recentTicks),
                strategy3(recentTicks),
                strategy4(recentTicks),
                strategy5(recentTicks),
                strategy6(recentTicks),
                strategy7_OFI(recentTicks),
                strategy8_Convexity(recentTicks)
        };

        String[] labels = {
                "Buy vs Sell Qty", "Avg Qty per Level", "Top Book Strength",
                "Price Momentum", "Depth Pressure", "Sudden Collapse",
                "Order Flow Imbalance", "Book Convexity"
        };

        for (int i = 0; i < strategies.length; i++) {
            Signal s = strategies[i];
            String label = labels[i];

            strategyStats.putIfAbsent(label, new EnumMap<>(Signal.class));
            strategyStats.get(label).putIfAbsent(s, new AtomicInteger(0));
            strategyStats.get(label).get(s).incrementAndGet();
        }

        if (buyVotes >= 7 && buyVotes > sellVotes) return Signal.BUY;
        if (sellVotes >= 7 && sellVotes > buyVotes) return Signal.SELL;
        return Signal.HOLD;
    }


    public static List<Map<String, Object>> getStrategyStats() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, EnumMap<Signal, AtomicInteger>> entry : strategyStats.entrySet()) {
            String strategy = entry.getKey();
            EnumMap<Signal, AtomicInteger> map = entry.getValue();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("strategy", strategy);
            row.put("BUY", map.getOrDefault(Signal.BUY, new AtomicInteger(0)).get());
            row.put("SELL", map.getOrDefault(Signal.SELL, new AtomicInteger(0)).get());
            row.put("HOLD", map.getOrDefault(Signal.HOLD, new AtomicInteger(0)).get());
            result.add(row);
        }
        return result;
    }

    private static Signal strategy1(List<Tick> ticks) {
        int buy = 0, sell = 0;
        for (Tick tick : ticks) {
            for (DepthPacket dp : tick.getMbpRowPacket()) {
                buy += dp.getBuyQuantity();
                sell += dp.getSellQuantity();
            }
        }
        return thresholdSignal(buy, sell, THRESHOLD_STRATEGY_1);
    }

    private static Signal strategy2(List<Tick> ticks) {
        int buy = 0, sell = 0, count = 0;
        for (Tick tick : ticks) {
            for (DepthPacket dp : tick.getMbpRowPacket()) {
                buy += dp.getBuyQuantity();
                sell += dp.getSellQuantity();
                count++;
            }
        }
        if (count == 0) return Signal.HOLD;
        return thresholdSignal((double) buy / count, (double) sell / count, THRESHOLD_STRATEGY_2);
    }

    private static Signal strategy3(List<Tick> ticks) {
        int bidStronger = 0, askStronger = 0;
        for (Tick tick : ticks) {
            if (!tick.getMbpRowPacket().isEmpty()) {
                DepthPacket top = tick.getMbpRowPacket().get(0);
                if (top.getBuyQuantity() > top.getSellQuantity()) bidStronger++;
                else askStronger++;
            }
        }
        return thresholdSignal(bidStronger, askStronger, THRESHOLD_STRATEGY_3);
    }

    private static Signal strategy4(List<Tick> ticks) {
        int momentumPositive = 0, momentumNegative = 0;
        int window = 5;

        for (int i = window; i < ticks.size(); i++) {
            float past = ticks.get(i - window).getLastTradedPrice();
            float now = ticks.get(i).getLastTradedPrice();
            if (now > past) momentumPositive++;
            else if (now < past) momentumNegative++;
        }

        if (momentumPositive > momentumNegative * THRESHOLD_STRATEGY_4_MULTIPLIER) return Signal.BUY;
        if (momentumNegative > momentumPositive * THRESHOLD_STRATEGY_4_MULTIPLIER) return Signal.SELL;
        return Signal.HOLD;
    }

    private static Signal strategy5(List<Tick> ticks) {
        double bidPressure = 0, askPressure = 0;
        for (Tick tick : ticks) {
            for (DepthPacket dp : tick.getMbpRowPacket()) {
                bidPressure += dp.getBuyQuantity() * dp.getBuyPrice();
                askPressure += dp.getSellQuantity() * dp.getSellPrice();
            }
        }
        return thresholdSignal(bidPressure, askPressure, THRESHOLD_STRATEGY_5);
    }

    private static Signal strategy6(List<Tick> ticks) {
        int dropCountBid = 0, dropCountAsk = 0;
        int window = 5;

        for (int i = window; i < ticks.size(); i++) {
            List<DepthPacket> prev = ticks.get(i - window).getMbpRowPacket();
            List<DepthPacket> curr = ticks.get(i).getMbpRowPacket();
            if (prev.isEmpty() || curr.isEmpty()) continue;

            double avgPrevBid = prev.stream().mapToDouble(DepthPacket::getBuyQuantity).average().orElse(0);
            double avgCurrBid = curr.stream().mapToDouble(DepthPacket::getBuyQuantity).average().orElse(0);
            if (avgCurrBid < avgPrevBid * THRESHOLD_STRATEGY_6_DROP) dropCountBid++;

            double avgPrevAsk = prev.stream().mapToDouble(DepthPacket::getSellQuantity).average().orElse(0);
            double avgCurrAsk = curr.stream().mapToDouble(DepthPacket::getSellQuantity).average().orElse(0);
            if (avgCurrAsk < avgPrevAsk * THRESHOLD_STRATEGY_6_DROP) dropCountAsk++;
        }

        if (dropCountAsk >= THRESHOLD_STRATEGY_6_COUNT) return Signal.BUY;
        if (dropCountBid >= THRESHOLD_STRATEGY_6_COUNT) return Signal.SELL;
        return Signal.HOLD;
    }

    private static Signal strategy7_OFI(List<Tick> ticks) {
        double smoothedOFI = 0;
        double alpha = 0.2;

        for (int i = 1; i < ticks.size(); i++) {
            List<DepthPacket> prev = ticks.get(i - 1).getMbpRowPacket();
            List<DepthPacket> curr = ticks.get(i).getMbpRowPacket();
            if (prev.isEmpty() || curr.isEmpty()) continue;

            DepthPacket prevTop = prev.get(0);
            DepthPacket currTop = curr.get(0);

            double buyChange = currTop.getBuyQuantity() - prevTop.getBuyQuantity();
            double sellChange = currTop.getSellQuantity() - prevTop.getSellQuantity();

            double deltaOFI = buyChange - sellChange;
            smoothedOFI = alpha * deltaOFI + (1 - alpha) * smoothedOFI;
        }

        if (smoothedOFI > THRESHOLD_STRATEGY_7_OFI) return Signal.BUY;
        if (smoothedOFI < -THRESHOLD_STRATEGY_7_OFI) return Signal.SELL;
        return Signal.HOLD;
    }

    private static Signal strategy8_Convexity(List<Tick> ticks) {
        double totalBidConvexity = 0, totalAskConvexity = 0;
        int levelsCount = 0;

        for (Tick tick : ticks) {
            List<DepthPacket> levels = tick.getMbpRowPacket();
            levelsCount = Math.max(levelsCount, levels.size());
            for (int i = 0; i < levels.size(); i++) {
                double decay = Math.exp(-i);
                totalBidConvexity += levels.get(i).getBuyQuantity() * decay;
                totalAskConvexity += levels.get(i).getSellQuantity() * decay;
            }
        }

        if (levelsCount == 0) return Signal.HOLD;

        double normBid = totalBidConvexity / ticks.size();
        double normAsk = totalAskConvexity / ticks.size();

        return thresholdSignal(normBid, normAsk, THRESHOLD_STRATEGY_8);
    }

    private static Signal thresholdSignal(double buy, double sell, double threshold) {
        if (buy > sell * threshold) return Signal.BUY;
        if (sell > buy * threshold) return Signal.SELL;
        return Signal.HOLD;
    }
}
