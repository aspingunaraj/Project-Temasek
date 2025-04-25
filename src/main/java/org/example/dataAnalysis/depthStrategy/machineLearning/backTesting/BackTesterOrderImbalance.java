package org.example.dataAnalysis.depthStrategy.machineLearning.backTesting;

import org.example.websocket.model.Tick;
import java.io.IOException;
import java.util.*;
import static org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.BackTesterUtility.*;

public class BackTesterOrderImbalance {

    private static final List<Integer> SYMBOL_IDS = Arrays.asList(
            1624, 2475, 3499, 3787, 4668, 4717, 10666, 10794, 14977, 18143);
    private static final String BASE_PATH = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/compressedTickDump_";

    private static final int AGGREGATION_WINDOW = 10;
    private static final double IMBALANCE_THRESHOLD = 0.55;
    private static final double TARGET_PROFIT = 0.004;  // 0.4%
    private static final double STOP_LOSS = 0.002;     // 0.25%
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
            double entryImbalance = 0;
            long entryTime = 0;
            int oppositeSignalCount = 0;

            for (int i = 0; i <= rawTicks.size() - AGGREGATION_WINDOW; i += AGGREGATION_WINDOW) {
                List<Tick> window = rawTicks.subList(i, i + AGGREGATION_WINDOW);
                Tick tick = aggregateTicks(window);
                double imbalance = calculateOBImbalance(tick);
                double price = tick.getLastTradedPrice();
                long time = tick.getLastTradedTime();

                double openPrice = tick.getOpen();
                double closePrice = tick.getClose();


                if (position == null) {
                    oppositeSignalCount = 0;
                    if (imbalance >= IMBALANCE_THRESHOLD && (closePrice > openPrice)) {
                        entryTick = tick;
                        entryPrice = price;
                        entryImbalance = imbalance;
                        entryTime = time;
                        position = "LONG";
                    } else if (imbalance <= -IMBALANCE_THRESHOLD && (openPrice > closePrice)) {
                        entryTick = tick;
                        entryPrice = price;
                        entryImbalance = imbalance;
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
                    } else if (imbalance <= -IMBALANCE_THRESHOLD) {
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
                    } else if (imbalance >= IMBALANCE_THRESHOLD) {
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
                    trades.add(new Trade(entryTime, time, entryPrice, price, entryImbalance, reason, pnl, entryType));

                    entryTick = null;
                    entryPrice = 0;
                    entryImbalance = 0;
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
}
