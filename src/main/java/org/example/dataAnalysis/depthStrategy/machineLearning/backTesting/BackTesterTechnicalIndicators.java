package org.example.dataAnalysis.depthStrategy.machineLearning.backTesting;

import org.example.websocket.model.Tick;
import java.io.IOException;
import java.util.*;
import static org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.BackTesterUtility.*;

public class BackTesterTechnicalIndicators {

    private static final List<Integer> SYMBOL_IDS = Arrays.asList(
            1624, 2475, 3499, 3787, 4668, 4717, 10666, 10794, 14977, 18143);
    private static final String BASE_PATH = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/compressedTickDump_";

    private static final int AGGREGATION_WINDOW = 10;
    private static final int EMA_WINDOW = 10;
    private static final int AMA_WINDOW = 10;
    private static final int RSI_WINDOW = 10;
    private static final int MACD_FAST_WINDOW = 12;
    private static final int MACD_SLOW_WINDOW = 26;
    private static final int ATR_WINDOW = 14;

    private static final int MIN_SCORE_TO_ENTER = 4;
    private static final double ATR_MULTIPLIER_STOPLOSS = 1.5;
    private static final double ATR_MULTIPLIER_TARGET = 2.5;

    private static final int OPPOSITE_SIGNAL_THRESHOLD = 1;

    public static void main(String[] args) throws IOException {

        int grandTotalTrades = 0, grandWins = 0, grandLosses = 0;
        double grandTotalPnL = 0;

        Map<Integer, Integer> scoreToTrades = new HashMap<>();
        Map<Integer, Integer> scoreToWins = new HashMap<>();

        for (int symbolId : SYMBOL_IDS) {
            String filePath = BASE_PATH + symbolId + ".json";
            List<Tick> rawTicks = loadTicks(filePath);
            List<Trade> trades = new ArrayList<>();
            List<Double> priceHistory = new ArrayList<>();
            List<Double> highHistory = new ArrayList<>();
            List<Double> lowHistory = new ArrayList<>();

            Tick entryTick = null;
            double entryPrice = 0;
            double atrAtEntry = 0;
            long entryTime = 0;
            int entryScore = 0;
            int oppositeSignalCount = 0;
            String position = null;

            for (int i = 0; i <= rawTicks.size() - AGGREGATION_WINDOW; i += AGGREGATION_WINDOW) {
                List<Tick> window = rawTicks.subList(i, i + AGGREGATION_WINDOW);
                Tick currentTick = aggregateTicks(window);

                double price = currentTick.getLastTradedPrice();
                double high = currentTick.getHigh();
                double low = currentTick.getLow();
                long time = currentTick.getLastTradedTime();
                int hour = (int) (time % 86400) / 3600;

                if (hour < 9 || hour > 14) continue;

                priceHistory.add(price);
                highHistory.add(high);
                lowHistory.add(low);

                if (priceHistory.size() < MACD_SLOW_WINDOW) continue;

                double ema = calculateEMA(priceHistory, EMA_WINDOW);
                double ama = calculateAMA(priceHistory, AMA_WINDOW);
                double rsi = calculateRSI(priceHistory, RSI_WINDOW);
                double macd = calculateMACD(priceHistory, MACD_FAST_WINDOW, MACD_SLOW_WINDOW);
                double atr = calculateATR(highHistory, lowHistory, priceHistory, ATR_WINDOW);
                double rocRSI = calculateRateOfChange(priceHistory, RSI_WINDOW);
                double rocMACD = calculateRateOfChange(priceHistory, MACD_FAST_WINDOW);

                int scoreBuy = 0;
                if (price > ema) scoreBuy++;
                if (ema > ama) scoreBuy++;
                if (rsi > 50) scoreBuy++;
                if (macd > 0) scoreBuy++;
                if (rocRSI > 0) scoreBuy++;
                if (rocMACD > 0) scoreBuy++;

                boolean shouldBuy = scoreBuy >= MIN_SCORE_TO_ENTER;

                int scoreSell = 0;
                if (price < ema) scoreSell++;
                if (ema < ama) scoreSell++;
                if (rsi < 50) scoreSell++;
                if (macd < 0) scoreSell++;
                if (rocRSI < 0) scoreSell++;
                if (rocMACD < 0) scoreSell++;

                boolean shouldSell = scoreSell >= MIN_SCORE_TO_ENTER;

                if (position == null) {
                    oppositeSignalCount = 0;
                    if (shouldBuy) {
                        entryTick = currentTick;
                        entryPrice = price;
                        atrAtEntry = atr;
                        entryTime = time;
                        entryScore = scoreBuy;
                        position = "LONG";
                    } else if (shouldSell) {
                        entryTick = currentTick;
                        entryPrice = price;
                        atrAtEntry = atr;
                        entryTime = time;
                        entryScore = scoreSell;
                        position = "SHORT";
                    }
                    continue;
                }

                double change = (price - entryPrice);
                boolean exit = false;
                String reason = "";

                double targetMove = ATR_MULTIPLIER_TARGET * atrAtEntry;
                double stopMove = ATR_MULTIPLIER_STOPLOSS * atrAtEntry;

                if (position.equals("LONG")) {
                    if (change >= targetMove) {
                        exit = true;
                        reason = "Target Hit";
                    } else if (change <= -stopMove) {
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
                    if (-change >= targetMove) {
                        exit = true;
                        reason = "Target Hit";
                    } else if (-change <= -stopMove) {
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
                    System.out.printf("EntryType: %s | EntryScore: %d | PnL: %.2f | ExitReason: %s\n", entryType, entryScore, pnl, reason);

                    scoreToTrades.put(entryScore, scoreToTrades.getOrDefault(entryScore, 0) + 1);
                    if (pnl > 0) {
                        scoreToWins.put(entryScore, scoreToWins.getOrDefault(entryScore, 0) + 1);
                    }

                    trades.add(new Trade(entryTime, time, entryPrice, price, entryScore, reason, pnl, entryType));

                    entryTick = null;
                    entryPrice = 0;
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

        System.out.println("\n====================== Win-rate by Entry Score ======================");
        for (Integer score : scoreToTrades.keySet()) {
            int trades = scoreToTrades.get(score);
            int wins = scoreToWins.getOrDefault(score, 0);
            double winRate = (trades > 0) ? (wins * 100.0 / trades) : 0;
            System.out.printf("Score: %d | Trades: %d | Wins: %d | WinRate: %.2f%%\n", score, trades, wins, winRate);
        }
    }

    // ===================== Calculation Methods =======================

    private static double calculateEMA(List<Double> prices, int window) {
        double k = 2.0 / (window + 1);
        double ema = prices.get(0);
        for (int i = 1; i < prices.size(); i++) {
            ema = prices.get(i) * k + ema * (1 - k);
        }
        return ema;
    }

    private static double calculateAMA(List<Double> prices, int window) {
        if (prices.size() < window) return prices.get(prices.size() - 1);
        double change = Math.abs(prices.get(prices.size() - 1) - prices.get(prices.size() - window));
        double volatility = 0;
        for (int i = prices.size() - window + 1; i < prices.size(); i++) {
            volatility += Math.abs(prices.get(i) - prices.get(i - 1));
        }
        double efficiencyRatio = (volatility == 0) ? 0 : change / volatility;
        double smoothingConstant = Math.pow(efficiencyRatio * (2.0 / (window + 1)), 2);
        double ama = prices.get(prices.size() - window);
        for (int i = prices.size() - window + 1; i < prices.size(); i++) {
            ama += smoothingConstant * (prices.get(i) - ama);
        }
        return ama;
    }

    private static double calculateRSI(List<Double> prices, int window) {
        double gain = 0, loss = 0;
        for (int i = prices.size() - window; i < prices.size() - 1; i++) {
            double change = prices.get(i + 1) - prices.get(i);
            if (change > 0) gain += change;
            else loss -= change;
        }
        if (loss == 0) return 100;
        double rs = gain / loss;
        return 100 - (100 / (1 + rs));
    }

    private static double calculateMACD(List<Double> prices, int fastWindow, int slowWindow) {
        double fastEMA = calculateEMA(prices.subList(prices.size() - fastWindow, prices.size()), fastWindow);
        double slowEMA = calculateEMA(prices.subList(prices.size() - slowWindow, prices.size()), slowWindow);
        return fastEMA - slowEMA;
    }

    private static double calculateATR(List<Double> highs, List<Double> lows, List<Double> closes, int window) {
        if (highs.size() < window) return 0;
        double atr = 0;
        for (int i = highs.size() - window; i < highs.size(); i++) {
            atr += (highs.get(i) - lows.get(i));
        }
        return atr / window;
    }

    private static double calculateRateOfChange(List<Double> prices, int window) {
        if (prices.size() < window) return 0;
        return (prices.get(prices.size() - 1) - prices.get(prices.size() - window)) / prices.get(prices.size() - window);
    }
}
