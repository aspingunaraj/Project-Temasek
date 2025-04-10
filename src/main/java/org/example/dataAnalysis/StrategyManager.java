package org.example.dataAnalysis;

import org.example.dataAnalysis.depthStrategy.StrategyOne;
import org.example.dataAnalysis.machineLearning.TickAdapter;
import org.example.websocket.model.Tick;
import org.tribuo.*;
import org.tribuo.classification.Label;
import org.tribuo.impl.ListExample;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.time.*;
import java.util.List;
import java.util.Map;

public class StrategyManager {

    private static Model<Label> cachedModel = null;

    public enum StrategyType {
        DEPTH_PACKET,
        ML_MODEL
    }

    /**
     * Selects and runs the appropriate strategy based on the current time and other conditions.
     *
     * @param recentTicks List of recent ticks for a symbol
     * @param symbolId    ID of the symbol being evaluated
     * @return Signal (BUY/SELL/HOLD) from the chosen strategy
     */
    public static StrategyOne.Signal strategySelector(List<Tick> recentTicks, int symbolId) {
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        LocalTime currentTime = nowIST.toLocalTime();

        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(14, 0);

        if (!currentTime.isBefore(start) && currentTime.isBefore(end)) {
            System.out.println("🧠 Using DepthPacketStrategies for symbol: " + symbolId);
            return StrategyOne.evaluateMarketSignal(recentTicks);
        } else {
            //System.out.println("🧠 Using ML Predictor for symbol: " + symbolId);
            return predictUsingML(recentTicks);
        }
    }


    private static StrategyOne.Signal predictUsingML(List<Tick> recentTicks) {
        try {
            if (recentTicks == null || recentTicks.size() < 200) {
                System.out.println("⛔ Not enough tick history for ML prediction.");
                return StrategyOne.Signal.HOLD;
            }

            // Load the model if not already loaded
            if (cachedModel == null) {
                File modelFile = new File("src/main/java/org/example/dataAnalysis/machineLearning/signal-model-v1.ser");
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(modelFile))) {
                    cachedModel = (Model<Label>) ois.readObject();
                    System.out.println("📦 ML Model loaded successfully.");
                }
            }

            // Use the latest tick for prediction
            Tick latestTick = recentTicks.get(recentTicks.size() - 1);
            Map<String, Double> featureMap = TickAdapter.extractFeatureMap(latestTick, recentTicks);
            if (featureMap == null) {
                System.out.println("⚠️ Could not extract features from latest tick.");
                return StrategyOne.Signal.HOLD;
            }

            // Convert to Tribuo input format
            List<Feature> features = featureMap.entrySet().stream()
                    .map(e -> new Feature(e.getKey(), e.getValue()))
                    .toList();

            Example<Label> input = new ListExample<>(new Label("UNKNOWN"), features);
            Prediction<Label> prediction = cachedModel.predict(input);

            String predicted = prediction.getOutput().getLabel();
            System.out.printf("📊 ML Prediction: %s | Scores: %s%n", predicted, prediction.getOutputScores());

            // Return signal based on predicted label
            return switch (predicted) {
                case "BUY" -> StrategyOne.Signal.BUY;
                case "SELL" -> StrategyOne.Signal.SELL;
                default -> StrategyOne.Signal.HOLD;
            };

        } catch (Exception e) {
            System.err.println("❌ ML Prediction Error: " + e.getMessage());
            e.printStackTrace();
            return StrategyOne.Signal.HOLD;
        }
    }



}
