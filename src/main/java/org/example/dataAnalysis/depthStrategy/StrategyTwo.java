package org.example.dataAnalysis.depthStrategy;

import org.example.websocket.model.Tick;
import weka.classifiers.Classifier;
import org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.MLUtils;
import org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.ModelSelector;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * StrategyTwo applies machine learning to intraday tick data using a progressive learning loop.
 * It labels past ticks using future price changes, trains models, and makes predictions with confidence filtering.
 */
public class StrategyTwo {

    // Configuration constants
    private static final int INITIAL_TRAINING_SIZE = 50; // Minimum number of labeled samples to start training
    private static final int RETRAIN_INTERVAL = 20; // Retrain after every 20 new samples
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.6; // Minimum confidence required to act on a signal
    private static final int MAX_DATA_SIZE = 200; // Max size of training data buffer per symbol
    private static final int LOOKAHEAD_SECONDS = 600; // Time to wait before labeling a tick based on price change
    private static final double TARGET_THRESHOLD = 0.004; // 0.4% price increase to label BUY
    private static final double STOPLOSS_THRESHOLD = 0.004; // 0.2% price drop to label SELL


    // Maps to maintain state per symbol
    private final Map<Integer, List<double[]>> featureMap = new HashMap<>(); // Symbol → List of extracted feature vectors
    private final Map<Integer, List<String>> labelMap = new HashMap<>(); // Symbol → Corresponding labels (BUY/SELL/HOLD)
    private final Map<Integer, Classifier> modelMap = new HashMap<>(); // Symbol → Trained ML model
    private final Map<Integer, Integer> retrainCounterMap = new HashMap<>(); // Symbol → Counter since last model retraining
    private final Map<Integer, Map<String, Integer>> signalSummaryMap = new HashMap<>(); // Symbol → Count of predictions by type
    private final Map<Integer, List<PendingLabel>> pendingLabelsMap = new HashMap<>(); // Symbol → List of ticks awaiting future outcome resolution

    private final MLUtils mlUtils = new MLUtils(ModelSelector.ModelType.RANDOM_FOREST); // Utility for ML ops
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(); // For periodic summary logging

    /**
     * Constructor starts a scheduled task to print a signal summary report every 30 seconds.
     */
    public StrategyTwo() {
        scheduler.scheduleAtFixedRate(this::printSummaryReport, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Internal class representing a tick whose outcome (label) is pending, based on future price.
     */
    private static class PendingLabel {
        final double[] features;
        final double referencePrice;
        final long referenceTime;

        PendingLabel(double[] features, double referencePrice, long referenceTime) {
            this.features = features;
            this.referencePrice = referencePrice;
            this.referenceTime = referenceTime;
        }
    }

    /**
     * Evaluates the trading signal based on the latest tick. Triggers model training and label finalization.
     *
     * @param tick Incoming tick data
     * @return Trading signal (BUY, SELL, HOLD)
     */
    public StrategyOne.Signal evaluateSignal(Tick tick) throws Exception {
        int symbolId = tick.getSecurityId();
        double[] features = mlUtils.extractFeatures(tick); // Feature extraction
        long now = Instant.ofEpochSecond(tick.getLastTradedTime()).getEpochSecond(); // Convert to epoch seconds
        double ltp = tick.getLastTradedPrice();

        // Initialize all symbol-specific maps if not already
        featureMap.putIfAbsent(symbolId, new ArrayList<>());
        labelMap.putIfAbsent(symbolId, new ArrayList<>());
        retrainCounterMap.putIfAbsent(symbolId, 0);
        signalSummaryMap.putIfAbsent(symbolId, new HashMap<>());
        pendingLabelsMap.putIfAbsent(symbolId, new ArrayList<>());

        // Finalize labels of older ticks if LOOKAHEAD_SECONDS has passed
        List<PendingLabel> pending = pendingLabelsMap.get(symbolId);
        Iterator<PendingLabel> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingLabel pendingLabel = iterator.next();
            if (now - pendingLabel.referenceTime >= LOOKAHEAD_SECONDS) {
                String resolvedLabel = resolveLabel(pendingLabel.referencePrice, ltp);
                featureMap.get(symbolId).add(pendingLabel.features);
                labelMap.get(symbolId).add(resolvedLabel);
                updateSummary(symbolId, resolvedLabel);
                iterator.remove();
            }
        }

        // Not enough training data yet → only log and return HOLD
        if (labelMap.get(symbolId).size() < INITIAL_TRAINING_SIZE) {
            pending.add(new PendingLabel(features, ltp, now));
            updateSummary(symbolId, "HOLD");
            return StrategyOne.Signal.HOLD;
        }

        // Check if model needs to be (re)trained
        int retrainCounter = retrainCounterMap.get(symbolId);
        if (!modelMap.containsKey(symbolId) || retrainCounter >= RETRAIN_INTERVAL) {
            modelMap.put(symbolId, mlUtils.trainModel(featureMap.get(symbolId), labelMap.get(symbolId)));
            retrainCounter = 0;
        }

        // Update retrain counter
        retrainCounterMap.put(symbolId, retrainCounter + 1);

        // Predict signal using the trained model
        Classifier model = modelMap.get(symbolId);
        MLUtils.PredictionResult result = mlUtils.predictWithConfidence(model, features);

        // Skip low-confidence predictions
        if (result.confidence < MIN_CONFIDENCE_THRESHOLD) {
            updateSummary(symbolId, "HOLD");
            return StrategyOne.Signal.HOLD;
        }

        // Update summary and return signal based on predicted label
        updateSummary(symbolId, result.label);
        return switch (result.label) {
            case "BUY" -> StrategyOne.Signal.BUY;
            case "SELL" -> StrategyOne.Signal.SELL;
            default -> StrategyOne.Signal.HOLD;
        };
    }

    /**
     * Resolves the label for a past tick by comparing current price to reference price.
     *
     * @param referencePrice Price at the time tick was received
     * @param currentPrice   Price after LOOKAHEAD_SECONDS
     * @return BUY / SELL / HOLD based on percentage change
     */
    private String resolveLabel(double referencePrice, double currentPrice) {
        double change = (currentPrice - referencePrice) / referencePrice;
        if (change >= TARGET_THRESHOLD) return "BUY";
        else if (change <= -STOPLOSS_THRESHOLD) return "SELL";
        else return "HOLD";
    }

    /**
     * Tracks the number of signals generated for each type for monitoring purposes.
     *
     * @param symbolId Symbol ID
     * @param label    Signal label (BUY / SELL / HOLD)
     */
    private void updateSummary(int symbolId, String label) {
        Map<String, Integer> summary = signalSummaryMap.get(symbolId);
        summary.put(label, summary.getOrDefault(label, 0) + 1);
    }

    /**
     * Prints a periodic summary of signal distribution per symbol.
     */
    public void printSummaryReport() {
        System.out.println("\n===== StrategyTwo ML Signal Summary Report =====");
        for (Map.Entry<Integer, Map<String, Integer>> entry : signalSummaryMap.entrySet()) {
            int symbolId = entry.getKey();
            Map<String, Integer> summary = entry.getValue();
            System.out.printf("Symbol ID: %d -> BUY: %d | SELL: %d | HOLD: %d\n",
                    symbolId,
                    summary.getOrDefault("BUY", 0),
                    summary.getOrDefault("SELL", 0),
                    summary.getOrDefault("HOLD", 0));
        }
        System.out.println("===============================================\n");
    }
}
