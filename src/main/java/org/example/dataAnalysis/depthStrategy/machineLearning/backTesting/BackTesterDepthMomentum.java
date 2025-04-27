package org.example.dataAnalysis.depthStrategy.machineLearning.backTesting;

import org.example.websocket.model.Tick;
import java.io.IOException;
import java.util.*;
import static org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.BackTesterUtility.*;

public class BackTesterDepthMomentum {

    private static final List<Integer> SYMBOL_IDS = Arrays.asList(
            1624, 2475, 3499, 3787, 4668, 4717, 10666, 10794, 14977, 18143);
    private static final String BASE_PATH = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/compressedTickDump_";

    // 🔧 Parameters you can easily tune
    private static final int AGGREGATION_WINDOW = 10;
    private static final int COMPARISON_TICK_COUNT = 5;
    private static final double DEPTH_MOMENTUM_THRESHOLD = 40000;
    private static final double TARGET_PROFIT = 0.003;
    private static final double STOP_LOSS = 0.002;
    private static final int OPPOSITE_SIGNAL_THRESHOLD = 1;

    private static final double VOLATILITY_LOWER_LIMIT = 0.02;
    private static final double VOLATILITY_UPPER_LIMIT = 0.8;
    private static final double SPREAD_LIMIT = 0.25;
    private static final int MIN_VOLUME = 300;
    private static final int MIN_SCORE_TO_TRADE = 4;

    public static void main(String[] args) throws IOException {

        int grandTotalTrades = 0, grandWins = 0, grandLosses = 0;
        double grandTotalPnL = 0;

        for (int symbolId : SYMBOL_IDS) {
            String filePath = BASE_PATH + symbolId + ".json";
            List<Tick> rawTicks = loadTicks(filePath);
            List<Trade> trades = new ArrayList<>();

            Deque<Tick> recentCompressedTicks = new LinkedList<>();
            Tick entryTick = null;
            double entryPrice = 0;
            long entryTime = 0;
            int oppositeSignalCount = 0;
            String position = null;

            for (int i = 0; i <= rawTicks.size() - AGGREGATION_WINDOW; i += AGGREGATION_WINDOW) {
                List<Tick> window = rawTicks.subList(i, i + AGGREGATION_WINDOW);
                Tick currentTick = aggregateTicks(window);

                if (recentCompressedTicks.size() < COMPARISON_TICK_COUNT) {
                    recentCompressedTicks.addLast(currentTick);
                    continue;
                }

                Tick tickToCompare = recentCompressedTicks.peekFirst();
                double depthMomentum = calculateDepthMomentum(tickToCompare, currentTick);
                double volatility = calculateShortTermVolatility(new ArrayList<>(recentCompressedTicks));
                double spread = calculateSpread(currentTick);
                int lastTradedQty = currentTick.getLastTradedQuantity();

                int score = 0;
                if (depthMomentum > DEPTH_MOMENTUM_THRESHOLD) score++;
                if (volatility >= VOLATILITY_LOWER_LIMIT && volatility <= VOLATILITY_UPPER_LIMIT) score++;
                if (spread <= SPREAD_LIMIT) score++;
                if (lastTradedQty >= MIN_VOLUME) score++;

                boolean shouldBuy = score >= MIN_SCORE_TO_TRADE && depthMomentum > 0;
                boolean shouldSell = score >= MIN_SCORE_TO_TRADE && depthMomentum < 0;

                double price = currentTick.getLastTradedPrice();
                long time = currentTick.getLastTradedTime();

                if (position == null) {
                    oppositeSignalCount = 0;
                    if (shouldBuy) {
                        entryTick = currentTick;
                        entryPrice = price;
                        entryTime = time;
                        position = "LONG";
                    } else if (shouldSell) {
                        entryTick = currentTick;
                        entryPrice = price;
                        entryTime = time;
                        position = "SHORT";
                    }
                    recentCompressedTicks.removeFirst();
                    recentCompressedTicks.addLast(currentTick);
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
                    } else if (shouldSell) {
                        oppositeSignalCount++;
                        if (oppositeSignalCount >= OPPOSITE_SIGNAL_THRESHOLD) {
                            exit = true;
                            reason = "Opposite Signal Triggered (Sell)";
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
                    } else if (shouldBuy) {
                        oppositeSignalCount++;
                        if (oppositeSignalCount >= OPPOSITE_SIGNAL_THRESHOLD) {
                            exit = true;
                            reason = "Opposite Signal Triggered (Buy)";
                        }
                    } else {
                        oppositeSignalCount = 0;
                    }
                }

                if (exit) {
                    double pnl = position.equals("LONG") ?
                            (price - entryPrice) * 100 : (entryPrice - price) * 100;
                    String entryType = position.equals("LONG") ? "Buy" : "Sell";
                    trades.add(new Trade(entryTime, time, entryPrice, price, 0, reason, pnl, entryType));

                    entryTick = null;
                    entryPrice = 0;
                    entryTime = 0;
                    position = null;
                    oppositeSignalCount = 0;
                }

                recentCompressedTicks.removeFirst();
                recentCompressedTicks.addLast(currentTick);
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

    private static double calculateDepthMomentum(Tick past, Tick curr) {
        double pastBuyQty = past.getTotalBuyQuantity();
        double pastSellQty = past.getTotalSellQuantity();
        double currBuyQty = curr.getTotalBuyQuantity();
        double currSellQty = curr.getTotalSellQuantity();

        return (currBuyQty - pastBuyQty) - (currSellQty - pastSellQty);
    }

    private static double calculateShortTermVolatility(List<Tick> ticks) {
        double mean = ticks.stream().mapToDouble(Tick::getLastTradedPrice).average().orElse(0.0);
        double variance = ticks.stream().mapToDouble(t -> Math.pow(t.getLastTradedPrice() - mean, 2)).sum() / ticks.size();
        return Math.sqrt(variance);
    }

    private static double calculateSpread(Tick tick) {
        if (tick.getMbpRowPacket() == null || tick.getMbpRowPacket().isEmpty()) return Double.MAX_VALUE;
        return tick.getMbpRowPacket().get(0).getSellPrice() - tick.getMbpRowPacket().get(0).getBuyPrice();
    }
}