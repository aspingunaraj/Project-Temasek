package org.example.dataAnalysis.depthStrategy;

import org.example.websocket.model.Tick;
import weka.classifiers.Classifier;
import org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.MLUtils;
import org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.ModelSelector;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StrategyTwo {

    private static final int INITIAL_TRAINING_SIZE = 50;
    private static final int RETRAIN_INTERVAL = 20;
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.6;
    private static final int MAX_DATA_SIZE = 200;

    // Symbol-wise storage
    private final Map<Integer, List<double[]>> featureMap = new HashMap<>();
    private final Map<Integer, List<String>> labelMap = new HashMap<>();
    private final Map<Integer, Classifier> modelMap = new HashMap<>();
    private final Map<Integer, Integer> retrainCounterMap = new HashMap<>();
    private final Map<Integer, Map<String, Integer>> signalSummaryMap = new HashMap<>();
    private final MLUtils mlUtils = new MLUtils(ModelSelector.ModelType.RANDOM_FOREST);

    // Scheduler to print summary every 30 seconds
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public StrategyTwo() {
        scheduler.scheduleAtFixedRate(this::printSummaryReport, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Evaluates the trading signal for a given tick using symbol-wise ML model.
     */
    public StrategyOne.Signal evaluateSignal(Tick tick) throws Exception {
        int symbolId = tick.getSecurityId();
        double[] features = mlUtils.extractFeatures(tick);

        // Initialize if absent
        featureMap.putIfAbsent(symbolId, new ArrayList<>());
        labelMap.putIfAbsent(symbolId, new ArrayList<>());
        retrainCounterMap.putIfAbsent(symbolId, 0);
        signalSummaryMap.putIfAbsent(symbolId, new HashMap<>());

        List<double[]> featuresList = featureMap.get(symbolId);
        List<String> labelsList = labelMap.get(symbolId);
        int retrainCounter = retrainCounterMap.get(symbolId);

        // Warm-up: store and skip prediction
        if (featuresList.size() < INITIAL_TRAINING_SIZE) {
            featuresList.add(features);
            labelsList.add("HOLD");
            updateSummary(symbolId, "HOLD");
            return StrategyOne.Signal.HOLD;
        }

        // Add new training data
        featuresList.add(features);
        labelsList.add("HOLD");
        if (featuresList.size() > MAX_DATA_SIZE) {
            featuresList.remove(0);
            labelsList.remove(0);
        }

        // Retrain if needed
        if (!modelMap.containsKey(symbolId) || retrainCounter >= RETRAIN_INTERVAL) {
            modelMap.put(symbolId, mlUtils.trainModel(featuresList, labelsList));
            retrainCounter = 0;
        }

        retrainCounterMap.put(symbolId, retrainCounter + 1);
        Classifier model = modelMap.get(symbolId);
        MLUtils.PredictionResult result = mlUtils.predictWithConfidence(model, features);

        if (result.confidence < MIN_CONFIDENCE_THRESHOLD) {
            updateSummary(symbolId, "HOLD");
            return StrategyOne.Signal.HOLD;
        }

        updateSummary(symbolId, result.label);

        return switch (result.label) {
            case "BUY" -> StrategyOne.Signal.BUY;
            case "SELL" -> StrategyOne.Signal.SELL;
            default -> StrategyOne.Signal.HOLD;
        };
    }

    private void updateSummary(int symbolId, String label) {
        Map<String, Integer> summary = signalSummaryMap.get(symbolId);
        summary.put(label, summary.getOrDefault(label, 0) + 1);
    }

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
