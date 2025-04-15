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
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.75;

    private static final List<PendingDecision> pendingDecisions = new ArrayList<>();
    private static final Map<String, Classifier> modelMap = new HashMap<>();
    private static final Map<String, Instances> headerMap = new HashMap<>();

    static {
        loadAllModels();
    }

    // Computes buy/sell pressure ratio from top level of order book
    private static double computeOrderBookPressureFeature(Tick tick) {
        List<DepthPacket> book = tick.getMbpRowPacket();
        if (book == null || book.isEmpty()) return 0.0;
        DepthPacket top = book.get(0);
        return top.getSellQuantity() == 0 ? top.getBuyQuantity() : (double) top.getBuyQuantity() / top.getSellQuantity();
    }

    // Calculates depth imbalance ratio across all order book levels
    private static double computeDepthImbalanceFeature(Tick tick) {
        List<DepthPacket> book = tick.getMbpRowPacket();
        int totalBuy = 0, totalSell = 0;
        for (DepthPacket dp : Optional.ofNullable(book).orElse(List.of())) {
            totalBuy += dp.getBuyQuantity();
            totalSell += dp.getSellQuantity();
        }
        return totalSell == 0 ? totalBuy : (double) (totalBuy - totalSell) / (totalBuy + totalSell);
    }

    // Measures convexity between top two order book levels
    private static double computeDepthConvexityFeature(Tick tick) {
        List<DepthPacket> book = tick.getMbpRowPacket();
        if (book == null || book.size() < 2) return 0.0;
        DepthPacket top = book.get(0), next = book.get(1);
        return (top.getBuyQuantity() - next.getBuyQuantity()) - (top.getSellQuantity() - next.getSellQuantity());
    }

    // Computes bid-ask spread from top order book level
    private static double computeBidAskSpreadFeature(Tick tick) {
        List<DepthPacket> book = tick.getMbpRowPacket();
        return (book == null || book.isEmpty()) ? 0.0 : book.get(0).getSellPrice() - book.get(0).getBuyPrice();
    }

    // Computes weighted pressure using top 5 order book levels
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

    // Computes the volume concentration at the current price
    private static double computeVolumeAtPriceFeature(Tick tick) {
        return tick.getVolumeTraded() == 0 ? 0.0 : tick.getLastTradedQuantity() / (double) tick.getVolumeTraded();
    }

    // Combines votes from all 6 strategies and applies signal fusion rule
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

            Signal signal;
            if (isModelReady(strategy)) {
                System.out.println("✅✅✅ Model is Ready ✅✅✅" + " " + strategy );
                signal = predictStrategySignal(strategy, feature);
            } else {
                System.out.println("✅✅✅ Model is not Ready ✅✅✅" + " " + strategy );
                signal = applyThresholdLogic(strategy, feature);
            }

            if (signal != Signal.HOLD) {
                pendingDecisions.add(new PendingDecision(
                        Instant.now().toEpochMilli(),
                        strategy,
                        feature,
                        tick.getLastTradedPrice(),
                        signal,
                        0
                ));
            }

            if (signal == Signal.BUY) buyVotes++;
            if (signal == Signal.SELL) sellVotes++;

        }



        if (buyVotes >= 4) return Signal.BUY;
        if (sellVotes >= 4) return Signal.SELL;
        return Signal.HOLD;
    }

    // Applies simple threshold logic for strategies if ML model is not available
    private static Signal applyThresholdLogic(String strategy, double feature) {
        return switch (strategy) {

            case "orderBookPressure", "top5Weight" -> {
                // These generally scale > 1.0 when BUY is dominant
                if (feature > 1.3) yield Signal.BUY;
                else if (feature < 0.9) yield Signal.SELL;
                else yield Signal.HOLD;
            }

            case "depthImbalance" -> {
                // Normalized between -1 and +1; near 0 = balance
                if (feature > 0.2) yield Signal.BUY;
                else if (feature < -0.2) yield Signal.SELL;
                else yield Signal.HOLD;
            }

            case "depthConvexity" -> {
                // Positive means BUY side stacked, negative = SELL side
                if (feature > 30) yield Signal.BUY;
                else if (feature < -30) yield Signal.SELL;
                else yield Signal.HOLD;
            }

            case "volumeAtPrice" -> {
                // High LTP/volume ratio → more aggressive trading
                if (feature > 0.015) yield Signal.BUY;
                else if (feature < 0.005) yield Signal.SELL;
                else yield Signal.HOLD;
            }

            case "bidAskSpread" -> {
                if (feature < 0.10) yield Signal.BUY;
                else if (feature > 0.13) yield Signal.SELL;
                else yield Signal.HOLD;
            }


            default -> Signal.HOLD;
        };
    }


    // Checks if training data and model for a strategy are ready
    private static boolean isModelReady(String strategy) {
        File file = new File(TRAINING_DATA_DIR + strategy + ".csv");
        if (!file.exists()) return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            long lineCount = reader.lines().skip(1).count();
            boolean modelExists = modelMap.get(strategy) != null;
            boolean headerExists = headerMap.get(strategy) != null;

            boolean ready = lineCount >= MIN_DATA_POINTS_REQUIRED && modelExists && headerExists;
            System.out.println("✅✅✅ Model Ready Check: " + strategy + " " + ready);


            return ready;
        } catch (IOException e) {
            return false;
        }
    }


    // Predicts signal using trained ML model for a strategy
    private static Signal predictStrategySignal(String strategy, double feature) {
        try {
            System.out.println("✅✅✅✅ PREDICTING THROUGH ML STRATEGY ✅✅✅✅✅");
            Classifier clf = modelMap.get(strategy);
            Instances hdr = headerMap.get(strategy);
            if (clf == null || hdr == null) return Signal.HOLD;

            Instance instance = new DenseInstance(2);
            instance.setDataset(hdr);
            instance.setValue(0, feature);

            double prediction = clf.classifyInstance(instance);
            double[] dist = clf.distributionForInstance(instance);
            String label = hdr.classAttribute().value((int) prediction);
            double confidence = dist[(int) prediction];

            if (confidence >= MIN_CONFIDENCE_THRESHOLD) {
                if (label.equals("BUY_SUCCESS") || label.equals("BUY_FAILURE")) return Signal.BUY;
                if (label.equals("SELL_SUCCESS") || label.equals("SELL_FAILURE")) return Signal.SELL;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Signal.HOLD;
    }

    // Trains all strategy models from their respective CSV training data
    public static void trainAllModels() {
        for (String strategy : STRATEGIES) {
            try {
                String csvPath = TRAINING_DATA_DIR + strategy + ".csv";
                File csvFile = new File(csvPath);
                if (!csvFile.exists()) continue;

                CSVLoader loader = new CSVLoader();
                loader.setSource(csvFile);
                Instances data = loader.getDataSet();
                data.setClassIndex(data.numAttributes() - 1);
                if (data.size() < MIN_DATA_POINTS_REQUIRED) continue;

                RandomForest model = new RandomForest();
                model.buildClassifier(data);

                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(MODEL_DIR + strategy + ".model"))) {
                    oos.writeObject(model);
                }

                ArffSaver saver = new ArffSaver();
                saver.setInstances(data);
                saver.setFile(new File(MODEL_DIR + strategy + "_header.arff"));
                saver.writeBatch();

                System.out.println("✅ Trained model for: " + strategy);
            } catch (Exception e) {
                System.err.println("❌ Training failed for " + strategy + ": " + e.getMessage());
            }
        }
    }

    // Logs feature and initial predicted label for training purposes
    // Logs feature and final outcome once trade is resolved
    private static void logTrainingData(String strategy, double feature, String finalLabel) {
        String filePath = TRAINING_DATA_DIR + strategy + ".csv";
        File file = new File(filePath);
        boolean fileExists = file.exists();
        try (FileWriter writer = new FileWriter(file, true)) {
            if (!fileExists) writer.write("timestamp,feature,label\n");
            writer.write(Instant.now().toEpochMilli() + "," + feature + "," + finalLabel + "\n");
        } catch (IOException e) {
            System.err.println("❌ Error logging training data for " + strategy + ": " + e.getMessage());
        }
    }


    // Loads all models and their ARFF headers into memory
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

            // Set TP and SL based on direction
            if (decision.direction == Signal.BUY) {
                target = decision.entryPrice * 1.01f;       // +1% target
                stopLoss = decision.entryPrice * 0.995f;    // -0.5% stop-loss
            } else {
                target = decision.entryPrice * 0.99f;       // -1% target for sell
                stopLoss = decision.entryPrice * 1.005f;    // +0.5% stop-loss for sell
            }

            // Increment tick counter for this decision
            decision.tickCount++;

            // Determine exit condition
            boolean hitTarget = (decision.direction == Signal.BUY) ? ltp >= target : ltp <= target;
            boolean hitStop = (decision.direction == Signal.BUY) ? ltp <= stopLoss : ltp >= stopLoss;
            boolean expired = decision.tickCount >= MAX_TICKS_TO_TRACK;

            // If trade is resolved
            if (hitTarget || hitStop || expired) {
                String label;

                if (hitTarget) {
                    label = (decision.direction == Signal.BUY) ? "BUY_SUCCESS" : "SELL_SUCCESS";
                } else if (hitStop || expired) {
                    label = (decision.direction == Signal.BUY) ? "BUY_FAILURE" : "SELL_FAILURE";
                } else {
                    label = "HOLD";
                }

                // Update the log line (overwrite previous 'FAILURE' placeholder)

                // inside evaluateOutcomes
                logTrainingData(decision.strategyName, decision.feature, label); // ✅ log only once after result is known

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
