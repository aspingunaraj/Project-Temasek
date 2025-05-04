// Updated BackTesterMLDriven.java with Exit Reasons and Symbol-wise Models
package org.example.dataAnalysis.depthStrategy.machineLearning.backTesting;

import org.example.websocket.model.Tick;
import weka.classifiers.Classifier;

import java.util.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.BackTesterUtility.*;

public class BackTesterMLDriven {

    private static final List<Integer> SYMBOL_IDS = Arrays.asList(
            1624, 3499, 3787, 4668, 4717, 10666, 10794, 14977, 18143);
    private static final String BASE_PATH = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/compressedTickDump_";

    private static final int AGGREGATION_WINDOW = 10;
    private static final int INITIAL_TRAINING_SIZE = 50;
    private static final int RETRAIN_INTERVAL = 20;
    private static final int LOOKAHEAD_TICKS = 100;
    private static final double TARGET_MOVE_THRESHOLD = 0.005;
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.6;
    private static final double TARGET_PROFIT_PERCENT = 0.008;
    private static final double STOP_LOSS_PERCENT = 0.004;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Kolkata"));

    public static void main(String[] args) throws Exception {

        int grandTotalTrades = 0, grandWins = 0, grandLosses = 0;
        double grandTotalPnL = 0;

        for (int symbolId : SYMBOL_IDS) {
            String filePath = BASE_PATH + symbolId + ".json";
            List<Tick> rawTicks = loadTicks(filePath);
            List<Tick> compressedTicks = new ArrayList<>();

            for (int i = 0; i <= rawTicks.size() - AGGREGATION_WINDOW; i += AGGREGATION_WINDOW) {
                compressedTicks.add(aggregateTicks(rawTicks.subList(i, i + AGGREGATION_WINDOW)));
            }

            if (compressedTicks.size() < INITIAL_TRAINING_SIZE + LOOKAHEAD_TICKS) {
                System.out.println("Not enough data for symbol " + symbolId + ". Skipping.");
                continue;
            }

            MLUtils mlUtils = new MLUtils(ModelSelector.ModelType.RANDOM_FOREST);
            List<double[]> featureList = new ArrayList<>();
            List<String> labelList = new ArrayList<>();

            Classifier model = null;
            Tick entryTick = null;
            double entryPrice = 0;
            long entryTime = 0;
            String position = null;
            int retrainCounter = 0;

            int wins = 0, losses = 0, trades = 0;
            double totalPnL = 0;

            for (int i = 0; i < compressedTicks.size() - LOOKAHEAD_TICKS; i++) {
                Tick tick = compressedTicks.get(i);
                double[] features = mlUtils.extractFeatures(tick);

                double futurePrice = compressedTicks.get(i + LOOKAHEAD_TICKS).getLastTradedPrice();
                double currentPrice = tick.getLastTradedPrice();
                double move = (futurePrice - currentPrice) / currentPrice;

                String label = move > TARGET_MOVE_THRESHOLD ? "BUY" : move < -TARGET_MOVE_THRESHOLD ? "SELL" : "HOLD";
                featureList.add(features);
                labelList.add(label);
                if (featureList.size() > 200) {
                    featureList.remove(0);
                    labelList.remove(0);
                }

                if (featureList.size() < INITIAL_TRAINING_SIZE) continue;

                if (model == null || retrainCounter >= RETRAIN_INTERVAL) {
                    model = mlUtils.trainModel(featureList, labelList);
                    retrainCounter = 0;
                }
                retrainCounter++;

                MLUtils.PredictionResult predictionResult = mlUtils.predictWithConfidence(model, features);
                if (predictionResult.confidence < MIN_CONFIDENCE_THRESHOLD) continue;

                String prediction = predictionResult.label;
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
