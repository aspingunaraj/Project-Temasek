package org.example.dataAnalysis;

import org.example.websocket.model.DepthPacket;
import org.example.websocket.model.Tick;

import java.util.List;

public class DepthPacketStrategies {

    public enum Signal {
        BUY, SELL, HOLD
    }

    public static Signal evaluateMarketSignal(List<Tick> recentTicks) {
        int buyVotes = 0, sellVotes = 0, holdVotes = 0;

        Signal[] strategies = {
                strategy1(recentTicks), // Buy vs Sell Qty
                strategy2(recentTicks), // Avg Qty per Level
                strategy3(recentTicks), // Top Book Strength
                strategy4(recentTicks), // Price Movement
                strategy5(recentTicks), // Depth Pressure
                strategy6(recentTicks), // Sudden Collapse
                strategy7_OFI(recentTicks), // New: Order Flow Imbalance
                strategy8_Convexity(recentTicks) // New: Convexity
        };

        String[] labels = {
                "Buy vs Sell Qty", "Avg Qty per Level", "Top Book Strength",
                "Price Movement", "Depth Pressure", "Sudden Collapse",
                "Order Flow Imbalance", "Book Convexity"
        };

        for (int i = 0; i < strategies.length; i++) {
            Signal s = strategies[i];
            System.out.println("📊 Strategy " + (i + 1) + " - " + labels[i] + " → " + s);
            if (s == Signal.BUY) buyVotes++;
            else if (s == Signal.SELL) sellVotes++;
            else holdVotes++;
        }

        System.out.println("✅ Votes → BUY: " + buyVotes + ", SELL: " + sellVotes + ", HOLD: " + holdVotes);
        if (buyVotes >= 6 && buyVotes > sellVotes) return Signal.BUY;
        if (sellVotes >= 6 && sellVotes > buyVotes) return Signal.SELL;
        return Signal.HOLD;
    }

    // ------------------- EXISTING STRATEGIES ----------------------

    private static Signal strategy1(List<Tick> ticks) {
        int buy = 0, sell = 0;
        for (Tick tick : ticks) {
            for (DepthPacket dp : tick.getMbpRowPacket()) {
                buy += dp.getBuyQuantity();
                sell += dp.getSellQuantity();
            }
        }
        return thresholdSignal(buy, sell, 1.2);
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
        return thresholdSignal(buy / count, sell / count, 1.3);
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
        return thresholdSignal(bidStronger, askStronger, 1.5);
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

        if (momentumPositive > momentumNegative * 1.5) return Signal.BUY;
        if (momentumNegative > momentumPositive * 1.5) return Signal.SELL;
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
        return thresholdSignal(bidPressure, askPressure, 1.15);
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
            if (avgCurrBid < avgPrevBid * 0.5) dropCountBid++;

            double avgPrevAsk = prev.stream().mapToDouble(DepthPacket::getSellQuantity).average().orElse(0);
            double avgCurrAsk = curr.stream().mapToDouble(DepthPacket::getSellQuantity).average().orElse(0);
            if (avgCurrAsk < avgPrevAsk * 0.5) dropCountAsk++;
        }

        if (dropCountAsk >= 5) return Signal.BUY;
        if (dropCountBid >= 5) return Signal.SELL;
        return Signal.HOLD;
    }


    // ------------------- NEW STRATEGIES ----------------------

    // Strategy 7: Order Flow Imbalance (OFI)
    private static Signal strategy7_OFI(List<Tick> ticks) {
        double smoothedOFI = 0;
        double alpha = 0.2; // smoothing factor

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

        if (smoothedOFI > 10) return Signal.BUY;
        if (smoothedOFI < -10) return Signal.SELL;
        return Signal.HOLD;
    }


    // Strategy 8: Convexity — book depth distribution
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

        return thresholdSignal(normBid, normAsk, 1.25);
    }


    // ------------------- Utility ----------------------

    private static Signal thresholdSignal(double buy, double sell, double threshold) {
        if (buy > sell * threshold) return Signal.BUY;
        if (sell > buy * threshold) return Signal.SELL;
        return Signal.HOLD;
    }
}
