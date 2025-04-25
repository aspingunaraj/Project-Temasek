package org.example.dataAnalysis.depthStrategy.machineLearning.backTesting;

import org.example.websocket.model.Tick;
import org.example.websocket.model.DepthPacket;

import java.io.IOException;
import java.util.*;

import static org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.BackTesterUtility.*;

public class BackTesterOBPI {

    private static final List<Integer> SYMBOL_IDS = Arrays.asList(
            1624, 2475, 3499, 3787, 4668, 4717, 10666, 10794, 14977, 18143);
    private static final String BASE_PATH = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/compressedTickDump_";

    private static final int AGGREGATION_WINDOW = 10;
    private static final double OBPI_THRESHOLD = 0.015; // ±5% OBPI trigger
    private static final double TARGET_PROFIT = 0.004;
    private static final double STOP_LOSS = 0.002;
    private static final int OPPOSITE_SIGNAL_THRESHOLD = 1;

    public static void main(String[] args) throws IOException {

        int grandTotalTrades = 0, grandWins = 0, grandLosses = 0;
        double grandTotalPnL = 0;

        for (int symbolId : SYMBOL_IDS) {
            String filePath = BASE_PATH + symbolId + ".json";
            List<Tick> rawTicks = loadTicks(filePath);
            List<Trade> trades = new ArrayList<>();

            Tick entryTick = null;
            double entryPrice = 0;
            String position = null;
            double entryOBPI = 0;
            long entryTime = 0;
            int oppositeSignalCount = 0;

            for (int i = 0; i <= rawTicks.size() - AGGREGATION_WINDOW; i += AGGREGATION_WINDOW) {
                List<Tick> window = rawTicks.subList(i, i + AGGREGATION_WINDOW);
                Tick tick = aggregateTicks(window);
                double obpi = computeOBPIV2Percent(window, tick);
                double price = tick.getLastTradedPrice();
                long time = tick.getLastTradedTime();

                double openPrice = tick.getOpen();
                double closePrice = tick.getClose();

                if (position == null) {
                    oppositeSignalCount = 0;
                    if (obpi >= OBPI_THRESHOLD && (closePrice > openPrice)) {
                        entryTick = tick;
                        entryPrice = price;
                        entryOBPI = obpi;
                        entryTime = time;
                        position = "LONG";
                    } else if (obpi <= -OBPI_THRESHOLD && (openPrice > closePrice)) {
                        entryTick = tick;
                        entryPrice = price;
                        entryOBPI = obpi;
                        entryTime = time;
                        position = "SHORT";
                    }
                    continue;
                }

                double change = (price - entryPrice) / entryPrice;
                boolean exit = false;
                String reason = "";

                if (position.equals("LONG")) {
                    if (change >= TARGET_PROFIT) {
                        exit = true;
                        reason = "Target Hit";
                    } else if (change <= -STOP_LOSS) {
                        exit = true;
                        reason = "Stoploss Hit";
                    } else if (obpi <= -OBPI_THRESHOLD) {
                        oppositeSignalCount++;
                        if (oppositeSignalCount >= OPPOSITE_SIGNAL_THRESHOLD) {
                            exit = true;
                            reason = "Opposite Signal Triggered";
                        }
                    } else {
                        oppositeSignalCount = 0;
                    }
                } else if (position.equals("SHORT")) {
                    if (-change >= TARGET_PROFIT) {
                        exit = true;
                        reason = "Target Hit";
                    } else if (-change <= -STOP_LOSS) {
                        exit = true;
                        reason = "Stoploss Hit";
                    } else if (obpi >= OBPI_THRESHOLD) {
                        oppositeSignalCount++;
                        if (oppositeSignalCount >= OPPOSITE_SIGNAL_THRESHOLD) {
                            exit = true;
                            reason = "Opposite Signal Triggered";
                        }
                    } else {
                        oppositeSignalCount = 0;
                    }
                }

                if (exit) {
                    double pnl = position.equals("LONG") ?
                            (price - entryPrice) * 100 : (entryPrice - price) * 100;
                    String entryType = position.equals("LONG") ? "Buy" : "Sell";
                    trades.add(new Trade(entryTime, time, entryPrice, price, entryOBPI, reason, pnl, entryType));

                    entryTick = null;
                    entryPrice = 0;
                    entryOBPI = 0;
                    entryTime = 0;
                    position = null;
                    oppositeSignalCount = 0;
                }
            }

            System.out.println("\nSymbol ID: " + symbolId);
            printTrades(trades);

            int wins = (int) trades.stream().filter(t -> t.pnl > 0).count();
            int losses = (int) trades.stream().filter(t -> t.pnl < 0).count();
            double totalPnL = trades.stream().mapToDouble(t -> t.pnl).sum();

            grandTotalTrades += trades.size();
            grandWins += wins;
            grandLosses += losses;
            grandTotalPnL += totalPnL;

            System.out.printf("Summary for Symbol %d => Trades: %d | Wins: %d | Losses: %d | Net P&L: %.2f\n",
                    symbolId, trades.size(), wins, losses, totalPnL);
        }

        System.out.println("\n====================== Overall Summary ======================");
        System.out.printf("Total Trades: %d | Total Wins: %d | Total Losses: %d | Net P&L: %.2f\n",
                grandTotalTrades, grandWins, grandLosses, grandTotalPnL);
    }

    // Computes enhanced OBPIv2 % using depth imbalance, spread sensitivity, and volume momentum
    private static double computeOBPIV2Percent(List<Tick> recentTicks, Tick aggregatedTick) {
        List<DepthPacket> depth = aggregatedTick.getMbpRowPacket();
        if (depth == null || depth.isEmpty()) return 0;

        // 1. Depth imbalance (multi-level, decaying weights)
        double weightedBid = 0, weightedAsk = 0;
        for (int i = 0; i < Math.min(5, depth.size()); i++) {
            double weight = 1.0 / (i + 1);
            weightedBid += weight * depth.get(i).getBuyQuantity();
            weightedAsk += weight * depth.get(i).getSellQuantity();
        }
        double epsilon = 1e-6;
        double depthImbalance = (weightedBid - weightedAsk) / (weightedBid + weightedAsk + epsilon);

        // 2. Spread factor (price proximity)
        DepthPacket top = depth.get(0);
        double bidPrice = top.getBuyPrice();
        double askPrice = top.getSellPrice();
        double spreadFactor = (askPrice - bidPrice) / (askPrice + bidPrice + epsilon);

        // 3. Volume momentum boost (current vs average)
        double currentVolume = aggregatedTick.getLastTradedQuantity();
        double avgVolume = recentTicks.stream().mapToDouble(Tick::getLastTradedQuantity).average().orElse(1);
        double volumeBoost = Math.max(0.5, Math.min(2.0, currentVolume / (avgVolume + epsilon)));

        return 100 * depthImbalance * spreadFactor * volumeBoost;
    }
}