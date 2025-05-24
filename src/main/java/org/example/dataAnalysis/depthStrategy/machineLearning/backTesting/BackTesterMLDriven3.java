package org.example.dataAnalysis.depthStrategy.machineLearning.backTesting;

import org.example.websocket.model.Tick;
import weka.classifiers.Classifier;
import weka.core.SerializationHelper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.BackTesterUtility.*;

public class BackTesterMLDriven3 {
//1406, 1624, 2475, 3499, 3787, 4668, 4717, 5097, 10666, 10794, 11630, 14977, 18143, 27066
    private static final List<Integer> SYMBOL_IDS = Arrays.asList(
        3787,3499,10794,1624,10666,14977,18143,4668,4717,2475,11630,5097,27066,1406,14366,8954,1491,12018,11915,24777,21951,383,14428,4973,11538,19084,21401,10576,8080,11006,15032,19543);
    private static final String BASE_PATH = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/testingData/compressedTickDump_";
    private static final String MODEL_PATH = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/models/model_global.model";

    private static final int AGGREGATION_WINDOW = 10;
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.6;
    private static final double TARGET_PROFIT_PERCENT = 0.008;
    private static final double STOP_LOSS_PERCENT = 0.004;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Kolkata"));

    public static void main(String[] args) throws Exception {
        int grandTotalTrades = 0, grandWins = 0, grandLosses = 0;
        double grandTotalPnL = 0;

        // ✅ Load the global model once
        Classifier model;
        try {
            model = (Classifier) SerializationHelper.read(MODEL_PATH);
            System.out.println("✅ Global model loaded from: " + MODEL_PATH);
        } catch (Exception e) {
            System.out.println("❌ Failed to load global model: " + e.getMessage());
            return;
        }

        MLUtils mlUtils = new MLUtils(ModelSelector.ModelType.RANDOM_FOREST);

        for (int symbolId : SYMBOL_IDS) {
            String filePath = BASE_PATH + symbolId + ".json";

            List<Tick> rawTicks = loadTicks(filePath);
            List<Tick> compressedTicks = new ArrayList<>();
            for (int i = 0; i <= rawTicks.size() - AGGREGATION_WINDOW; i += AGGREGATION_WINDOW) {
                compressedTicks.add(aggregateTicks(rawTicks.subList(i, i + AGGREGATION_WINDOW)));
            }

            if (compressedTicks.size() < 100) {
                System.out.println("Not enough compressed data for symbol " + symbolId + ". Skipping.");
                continue;
            }

            Tick entryTick = null;
            double entryPrice = 0;
            long entryTime = 0;
            String position = null;

            int trades = 0, wins = 0, losses = 0;
            double totalPnL = 0;

            for (Tick tick : compressedTicks) {
                double[] features = mlUtils.extractFeatures(tick);
                MLUtils.PredictionResult result = mlUtils.predictWithConfidence(model, features);

                if (result.confidence < MIN_CONFIDENCE_THRESHOLD) continue;

                String prediction = result.label;

                if (position == null) {
                    if (prediction.equals("BUY")) {
                        position = "LONG";
                        entryPrice = tick.getLastTradedPrice();
                        entryTime = tick.getLastTradedTime();
                        entryTick = tick;
                    } else if (prediction.equals("SELL")) {
                        position = "SHORT";
                        entryPrice = tick.getLastTradedPrice();
                        entryTime = tick.getLastTradedTime();
                        entryTick = tick;
                    }
                    continue;
                }

                double exitPrice = tick.getLastTradedPrice();
                double priceChange = (exitPrice - entryPrice) / entryPrice;
                boolean shouldExit = false;
                String exitReason = "";

                if (position.equals("LONG")) {
                    if (priceChange >= TARGET_PROFIT_PERCENT) {
                        shouldExit = true;
                        exitReason = "Target Achieved";
                    } else if (priceChange <= -STOP_LOSS_PERCENT) {
                        shouldExit = true;
                        exitReason = "Stop Loss Triggered";
                    } else if (prediction.equals("SELL")) {
                        shouldExit = true;
                        exitReason = "Opposite Signal Triggered";
                    }
                } else if (position.equals("SHORT")) {
                    if (-priceChange >= TARGET_PROFIT_PERCENT) {
                        shouldExit = true;
                        exitReason = "Target Achieved";
                    } else if (-priceChange <= -STOP_LOSS_PERCENT) {
                        shouldExit = true;
                        exitReason = "Stop Loss Triggered";
                    } else if (prediction.equals("BUY")) {
                        shouldExit = true;
                        exitReason = "Opposite Signal Triggered";
                    }
                }

                if (shouldExit) {
                    double pnl = (position.equals("LONG") ? (exitPrice - entryPrice) : (entryPrice - exitPrice)) * 100;
                    trades++;
                    if (pnl > 0) wins++; else losses++;
                    totalPnL += pnl;

                    System.out.printf("Trade: %s | Entry: %.2f @ %s | Exit: %.2f @ %s | PnL: %.2f | Reason: %s\n",
                            position, entryPrice, FORMATTER.format(Instant.ofEpochSecond(entryTime)),
                            exitPrice, FORMATTER.format(Instant.ofEpochSecond(tick.getLastTradedTime())), pnl, exitReason);

                    position = null; entryPrice = 0; entryTick = null; entryTime = 0;
                }
            }

            grandTotalTrades += trades;
            grandWins += wins;
            grandLosses += losses;
            grandTotalPnL += totalPnL;

            System.out.printf("\nSummary for Symbol %d => Trades: %d | Wins: %d | Losses: %d | Net P&L: %.2f\n",
                    symbolId, trades, wins, losses, totalPnL);
            System.out.println("---------------------------------------------------------------");
        }

        System.out.println("\n====================== Overall Summary ======================");
        System.out.printf("Total Trades: %d | Total Wins: %d | Total Losses: %d | Net P&L: %.2f\n",
                grandTotalTrades, grandWins, grandLosses, grandTotalPnL);
    }
}
