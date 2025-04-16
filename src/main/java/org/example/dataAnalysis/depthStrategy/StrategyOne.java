package org.example.dataAnalysis.depthStrategy;

import org.example.logger.LogWebSocketHandler;
import org.example.websocket.model.DepthPacket;
import org.example.websocket.model.Tick;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.converters.ArffSaver;

import java.io.*;
import java.time.Instant;
import java.util.*;

public class StrategyOne {

    public enum Signal {
        BUY, SELL, HOLD
    }

    private static final List<String> STRATEGIES = List.of(
            "orderBookPressure", "depthImbalance", "depthConvexity",
            "bidAskSpread", "top5Weight", "volumeAtPrice"
    );
    private static final String TRAINING_DATA_DIR = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/";
    private static final String MODEL_DIR = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/models/";
    private static final int MAX_TICKS_TO_TRACK = 200000;
    private static final int MIN_DATA_POINTS_REQUIRED = 10;
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.85;

    private static final List<PendingDecision> pendingDecisions = new ArrayList<>();
    private static final Map<String, Classifier> modelMap = new HashMap<>();
    private static final Map<String, Instances> headerMap = new HashMap<>();

    static {
        loadAllModels();
    }

    private static double computeOrderBookPressureFeature(Tick tick) {
        List<DepthPacket> book = tick.getMbpRowPacket();
        if (book == null || book.isEmpty()) return 0.0;
        DepthPacket top = book.get(0);
        return top.getSellQuantity() == 0 ? top.getBuyQuantity() : (double) top.getBuyQuantity() / top.getSellQuantity();
    }

    private static double computeDepthImbalanceFeature(Tick tick) {
        List<DepthPacket> book = tick.getMbpRowPacket();
        int totalBuy = 0, totalSell = 0;
        for (DepthPacket dp : Optional.ofNullable(book).orElse(List.of())) {
            totalBuy += dp.getBuyQuantity();
            totalSell += dp.getSellQuantity();
        }
        return totalSell == 0 ? totalBuy : (double) (totalBuy - totalSell) / (totalBuy + totalSell);
    }

    private static double computeDepthConvexityFeature(Tick tick) {
        List<DepthPacket> book = tick.getMbpRowPacket();
        if (book == null || book.size() < 2) return 0.0;
        DepthPacket top = book.get(0), next = book.get(1);
        return (top.getBuyQuantity() - next.getBuyQuantity()) - (top.getSellQuantity() - next.getSellQuantity());
    }

    private static double computeBidAskSpreadFeature(Tick tick) {
        List<DepthPacket> book = tick.getMbpRowPacket();
        return (book == null || book.isEmpty()) ? 0.0 : book.get(0).getSellPrice() - book.get(0).getBuyPrice();
    }

    private static double computeTop5WeightedPressureFeature(Tick tick) {
        List<DepthPacket> book = tick.getMbpRowPacket();
        double weightedBuy = 0, weightedSell = 0;
        for (int i = 0; i < Math.min(book.size(), 5); i++) {
            DepthPacket dp = book.get(i);
            weightedBuy += dp.getBuyQuantity() / (i + 1.0);
            weightedSell += dp.getSellQuantity() / (i + 1.0);
        }
        return weightedSell == 0 ? weightedBuy : weightedBuy / weightedSell;
    }

    private static double computeVolumeAtPriceFeature(Tick tick) {
        return tick.getVolumeTraded() == 0 ? 0.0 : tick.getLastTradedQuantity() / (double) tick.getVolumeTraded();
    }

    public static Signal evaluateSignalFusion(Tick tick) {
        Map<String, Double> features = Map.of(
                "orderBookPressure", computeOrderBookPressureFeature(tick),
                "depthImbalance", computeDepthImbalanceFeature(tick),
                "depthConvexity", computeDepthConvexityFeature(tick),
                "bidAskSpread", computeBidAskSpreadFeature(tick),
                "top5Weight", computeTop5WeightedPressureFeature(tick),
                "volumeAtPrice", computeVolumeAtPriceFeature(tick)
        );

        int buyVotes = 0, sellVotes = 0;

        for (Map.Entry<String, Double> entry : features.entrySet()) {
            String strategy = entry.getKey();
            double feature = entry.getValue();

            Signal signal = isModelReady(strategy)
                    ? predictStrategySignal(strategy, feature)
                    : Signal.HOLD;

            // ✅ Always log the decision for training/outcome tracking
            pendingDecisions.add(new PendingDecision(
                    Instant.now().toEpochMilli(),
                    strategy,
                    feature,
                    tick.getLastTradedPrice(),
                    signal,
                    0
            ));

            // ✅ Count vote only if sufficient data is available
            if (hasMinimumTrainingData(strategy, 110)) {
                if (signal == Signal.BUY) buyVotes++;
                if (signal == Signal.SELL) sellVotes++;
            }
        }

        if (buyVotes >= 4) return Signal.BUY;
        if (sellVotes >= 4) return Signal.SELL;
        return Signal.HOLD;
    }

    private static boolean hasMinimumTrainingData(String strategy, int minRows) {
        File file = new File(TRAINING_DATA_DIR + strategy + ".csv");
        if (!file.exists()) return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.lines().skip(1).count() >= minRows;
        } catch (IOException e) {
            return false;
        }
    }




    private static boolean isModelReady(String strategy) {
        File file = new File(TRAINING_DATA_DIR + strategy + ".csv");
        if (!file.exists()) return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            long lineCount = reader.lines().skip(1).count();
            boolean modelExists = modelMap.get(strategy) != null;
            boolean headerExists = headerMap.get(strategy) != null;
            return lineCount >= MIN_DATA_POINTS_REQUIRED && modelExists && headerExists;
        } catch (IOException e) {
            return false;
        }
    }

    private static Signal predictStrategySignal(String strategy, double feature) {
        try {
            Classifier clf = modelMap.get(strategy);
            Instances hdr = headerMap.get(strategy);
            if (clf == null || hdr == null) return Signal.HOLD;

            Instance instance = new DenseInstance(2);
            instance.setDataset(hdr);
            instance.setValue(0, feature);

            double predictionIndex = clf.classifyInstance(instance);
            double[] dist = clf.distributionForInstance(instance);
            double confidence = dist[(int) predictionIndex];
            String label = hdr.classAttribute().value((int) predictionIndex);

            if (confidence >= MIN_CONFIDENCE_THRESHOLD) {
                if (label.startsWith("BUY")) return Signal.BUY;
                if (label.startsWith("SELL")) return Signal.SELL;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Signal.HOLD;
    }

    public static void trainAllModels() {
        final int MIN_DATA_ROWS_FOR_TRAINING = 100;

        for (String strategy : STRATEGIES) {
            try {
                String csvPath = TRAINING_DATA_DIR + strategy + ".csv";
                File csvFile = new File(csvPath);
                if (!csvFile.exists()) {
                    System.out.println("⚠️ No training file found for: " + strategy);
                    continue;
                }

                // Count rows (excluding header)
                long dataLines = new BufferedReader(new FileReader(csvFile)).lines().skip(1).count();
                if (dataLines < MIN_DATA_ROWS_FOR_TRAINING) {
                    System.out.printf("⏳ Skipping %s — only %d rows available (needs %d)%n",
                            strategy, dataLines, MIN_DATA_ROWS_FOR_TRAINING);
                    continue;
                }

                // Load CSV into Instances
                CSVLoader loader = new CSVLoader();
                loader.setSource(csvFile);
                Instances data = loader.getDataSet();
                data.setClassIndex(data.numAttributes() - 1);

                // Train RandomForest model
                RandomForest model = new RandomForest();
                model.buildClassifier(data);

                // Save model
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(MODEL_DIR + strategy + ".model"))) {
                    oos.writeObject(model);
                }

                // Save ARFF header
                ArffSaver saver = new ArffSaver();
                saver.setInstances(data);
                saver.setFile(new File(MODEL_DIR + strategy + "_header.arff"));
                saver.writeBatch();

                System.out.println("✅ Model trained successfully for: " + strategy);
            } catch (Exception e) {
                System.err.printf("❌ Training failed for %s: %s%n", strategy, e.getMessage());
            }
        }
    }


    private static void logTrainingData(String strategy, double feature, String finalLabel) {
        String filePath = TRAINING_DATA_DIR + strategy + ".csv";
        File file = new File(filePath);
        boolean fileExists = file.exists();

        try (FileWriter writer = new FileWriter(file, true)) {
            if (!fileExists) writer.write("timestamp,feature,label\n");
            writer.write(Instant.now().toEpochMilli() + "," + feature + "," + finalLabel + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        // ✅ Enforce a max of 5000 rows
        trimTrainingFileIfNeeded(filePath, 5000);
    }

    private static void trimTrainingFileIfNeeded(String filePath, int maxRows) {
        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
                lines = reader.lines().toList();
            }

            if (lines.size() > maxRows + 1) { // +1 for header
                List<String> trimmed = new ArrayList<>();
                trimmed.add(lines.get(0)); // header
                trimmed.addAll(lines.subList(lines.size() - maxRows, lines.size()));

                try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, false))) {
                    for (String line : trimmed) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void loadAllModels() {
        for (String strategy : STRATEGIES) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(MODEL_DIR + strategy + ".model"))) {
                Classifier clf = (Classifier) ois.readObject();
                Instances hdr = new Instances(new BufferedReader(new FileReader(MODEL_DIR + strategy + "_header.arff")));
                hdr.setClassIndex(hdr.numAttributes() - 1);
                modelMap.put(strategy, clf);
                headerMap.put(strategy, hdr);
            } catch (Exception e) {
                System.err.println("⚠️ Could not load model for " + strategy);
            }
        }
    }

    public static void evaluateOutcomes(Tick newTick) {
        float ltp = newTick.getLastTradedPrice();
        Iterator<PendingDecision> it = pendingDecisions.iterator();

        while (it.hasNext()) {
            PendingDecision decision = it.next();
            float target, stopLoss;

            // Set TP and SL based on inferred direction (even if original was HOLD)

            Signal assumedDirection;
            if (decision.direction == Signal.HOLD) {
                // Randomly choose between BUY or SELL for HOLD cases
                assumedDirection = Math.random() < 0.5 ? Signal.BUY : Signal.SELL;
            } else {
                assumedDirection = decision.direction;
            }
            if (assumedDirection == Signal.BUY) {
                target = decision.entryPrice * 1.003f;     // +0.3% profit target
                stopLoss = decision.entryPrice * 0.9985f;  // -0.15% stop-loss
            } else {
                target = decision.entryPrice * 0.997f;     // -0.3% profit target
                stopLoss = decision.entryPrice * 1.0015f;  // +0.15% stop-loss
            }


            decision.tickCount++;

            boolean hitTarget = (assumedDirection == Signal.BUY) ? ltp >= target : ltp <= target;
            boolean hitStop = (assumedDirection == Signal.BUY) ? ltp <= stopLoss : ltp >= stopLoss;
            boolean expired = decision.tickCount >= MAX_TICKS_TO_TRACK;

            if (hitTarget || hitStop || expired) {
                String label;

                if (hitTarget) {
                    label = (assumedDirection == Signal.BUY) ? "BUY_SUCCESS" : "SELL_SUCCESS";
                } else if (hitStop || expired) {
                    label = (assumedDirection == Signal.BUY) ? "BUY_FAILURE" : "SELL_FAILURE";
                } else {
                    label = "HOLD";
                }

                logTrainingData(decision.strategyName, decision.feature, label);
                it.remove();
            }
        }
    }


    private static class PendingDecision {
        long timestamp;
        String strategyName;
        double feature;
        float entryPrice;
        Signal direction;
        int tickCount;

        public PendingDecision(long timestamp, String strategyName, double feature, float entryPrice, Signal direction, int tickCount) {
            this.timestamp = timestamp;
            this.strategyName = strategyName;
            this.feature = feature;
            this.entryPrice = entryPrice;
            this.direction = direction;
            this.tickCount = tickCount;
        }
    }
}
