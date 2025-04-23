package org.example.dataAnalysis.depthStrategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.websocket.model.DepthPacket;
import org.example.websocket.model.Tick;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.converters.ArffSaver;

import java.io.*;
import java.time.Instant;
import java.util.*;

/**
 * StrategyOne evaluates live ticks using ML-based signal generation.
 * It supports progressive learning (soak-in + retraining) and tracks per-strategy accuracy.
 */
public class StrategyOne {

    public enum Signal {
        BUY, SELL, HOLD
    }

    private static final String STRATEGY = "orderBookPressure";
    private static final String TRAINING_DATA_DIR = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/";
    private static final String MODEL_DIR = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/models/";

    // Feature-based strategy tracking
    private static final int MAX_TICKS_TO_TRACK = 200000;
    private static final int SOAK_IN_COUNT = 100;
    private static final int RETRAIN_INTERVAL = 100;
    private static final double CONFIDENCE_THRESHOLD = 0.999;

    private static final List<PendingDecision> pendingDecisions = new ArrayList<>();
    private static final List<LabeledSample> labeledSamples = new ArrayList<>();
    private static final Map<String, Classifier> modelMap = new HashMap<>();
    private static final Map<String, Instances> headerMap = new HashMap<>();

    // Live accuracy counters
    private static int totalPredictions = 0;
    private static int correctPredictions = 0;
    private static int skippedPredictions = 0;

    static {
        loadModel();
    }

    /**
     * Computes features used in BackTester for the orderBookPressure strategy.
     */
    private static double[] computeFeatures(Tick tick) {
        List<DepthPacket> book = tick.getMbpRowPacket();
        if (book == null || book.isEmpty()) return new double[]{0, 0, 0};

        int totalBuy = 0, totalSell = 0;
        double topBuy = book.get(0).getBuyPrice();
        double topSell = book.get(0).getSellPrice();

        for (DepthPacket dp : book) {
            totalBuy += dp.getBuyQuantity();
            totalSell += dp.getSellQuantity();
        }

        double pressure = (totalBuy + totalSell == 0) ? 0.0 : (double)(totalBuy - totalSell) / (totalBuy + totalSell);
        double imbalance = totalBuy - totalSell;
        double spread = topSell - topBuy;

        return new double[]{pressure, imbalance, spread};
    }

    /**
     * Called from WebSocket tick stream. Evaluates signal using live ML model.
     */
    public static Signal evaluateSignalFusion(Tick tick) {
        double[] features = computeFeatures(tick);
        double feature = features[0]; // main feature for labeling decisions
        Signal signal = Signal.HOLD;

        // Check if model is ready before using it
        if (isModelReady()) {
            try {
                Classifier clf = modelMap.get(STRATEGY);
                Instances hdr = headerMap.get(STRATEGY);
                Instance instance = new DenseInstance(4);
                instance.setDataset(hdr);
                instance.setValue(0, features[0]);
                instance.setValue(1, features[1]);
                instance.setValue(2, features[2]);

                double predictionIndex = clf.classifyInstance(instance);
                double[] dist = clf.distributionForInstance(instance);
                double confidence = dist[(int) predictionIndex];

                if (confidence >= CONFIDENCE_THRESHOLD) {
                    String label = hdr.classAttribute().value((int) predictionIndex);
                    if (label.equals("SUCCESS")) signal = Signal.BUY;
                    else if (label.equals("FAILURE")) signal = Signal.SELL;

                    totalPredictions++;
                } else {
                    skippedPredictions++;
                }

                // Save for post-outcome evaluation
                pendingDecisions.add(new PendingDecision(
                        Instant.now().toEpochMilli(),
                        feature,
                        tick.getLastTradedPrice(),
                        Signal.HOLD,
                        0,
                        tick.getSecurityId() // NEW
                ));

            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Add pending decision even without model
            pendingDecisions.add(new PendingDecision(
                    Instant.now().toEpochMilli(),
                    feature,
                    tick.getLastTradedPrice(),
                    signal,
                    0,
                    tick.getSecurityId() // NEW
            ));

        }

        return signal;
    }
    /**
     * Evaluates all pending decisions using new tick's LTP.
     * Labels outcome and retrains the model if required.
     */
    public static void evaluateOutcomes(Tick newTick) {
        float ltp = newTick.getLastTradedPrice();
        Iterator<PendingDecision> it = pendingDecisions.iterator();

        while (it.hasNext()) {
            PendingDecision d = it.next();

            if (newTick.getSecurityId() != d.symbolId) continue;

            float target, stopLoss;
            Signal dir = d.direction == Signal.HOLD
                    ? (Math.random() < 0.5 ? Signal.BUY : Signal.SELL)
                    : d.direction;

            target = (dir == Signal.BUY) ? d.entryPrice * 1.002f : d.entryPrice * 0.998f;
            stopLoss = (dir == Signal.BUY) ? d.entryPrice * 0.998f : d.entryPrice * 1.002f;

            d.tickCount++;

            boolean hitTarget = (dir == Signal.BUY) ? ltp >= target : ltp <= target;
            boolean hitStop = (dir == Signal.BUY) ? ltp <= stopLoss : ltp >= stopLoss;
            boolean expired = d.tickCount >= MAX_TICKS_TO_TRACK;

            if (hitTarget || hitStop || expired) {
                String label = hitTarget ? "SUCCESS" : "FAILURE";
                labeledSamples.add(new LabeledSample(d.featureVector(), label));
                if (d.direction != Signal.HOLD) {
                    if ((label.equals("SUCCESS") && d.direction == Signal.BUY) ||
                            (label.equals("FAILURE") && d.direction == Signal.SELL)) {
                        correctPredictions++;
                    }
                }
                it.remove();
            }
        }


        System.out.println("Pending Decisions: " + pendingDecisions.size());
        System.out.println("Labeled Samples: " + labeledSamples.size());


        // Retrain model progressively if enough new samples added
        if (labeledSamples.size() >= SOAK_IN_COUNT &&
                (labeledSamples.size() - SOAK_IN_COUNT) % RETRAIN_INTERVAL == 0) {
            retrainModel();
        }

        // Print live summary every 1000 outcomes
        if (labeledSamples.size() % RETRAIN_INTERVAL == 0 && labeledSamples.size() > 0) {
            printLiveSummary();
        }
    }

    /**
     * Builds Weka model using labeled data and persists model + header.
     */
    private static void retrainModel() {
        try {
            Instances dataset = createInstances(labeledSamples, "LiveModel");
            RandomForest model = new RandomForest();
            model.buildClassifier(dataset);

            modelMap.put(STRATEGY, model);
            headerMap.put(STRATEGY, dataset);

            System.out.println("🧠 Model retrained with " + labeledSamples.size() + " labeled samples (stored in-memory).");


            modelMap.put(STRATEGY, model);
            headerMap.put(STRATEGY, dataset);

            System.out.println("🧠 Model retrained with " + labeledSamples.size() + " labeled samples.");

        } catch (Exception e) {
            System.err.println("❌ Retraining failed: " + e.getMessage());
        }
    }
    /**
     * Builds Weka Instances from labeled sample list.
     */
    private static Instances createInstances(List<LabeledSample> data, String name) {
        ArrayList<Attribute> attrs = new ArrayList<>();
        attrs.add(new Attribute("pressure"));
        attrs.add(new Attribute("imbalance"));
        attrs.add(new Attribute("spread"));
        List<String> classVals = Arrays.asList("SUCCESS", "FAILURE");
        attrs.add(new Attribute("class", classVals));

        Instances dataset = new Instances(name, attrs, data.size());
        dataset.setClassIndex(attrs.size() - 1);

        for (LabeledSample s : data) {
            Instance inst = new DenseInstance(attrs.size());
            inst.setValue(attrs.get(0), s.features[0]);
            inst.setValue(attrs.get(1), s.features[1]);
            inst.setValue(attrs.get(2), s.features[2]);
            inst.setValue(attrs.get(3), s.label);
            dataset.add(inst);
        }

        return dataset;
    }

    /**
     * Checks if both model and header are loaded and sufficient training has occurred.
     */
    private static boolean isModelReady() {
        return labeledSamples.size() >= SOAK_IN_COUNT &&
                modelMap.containsKey(STRATEGY) &&
                headerMap.containsKey(STRATEGY);
    }

    /**
     * Prints current accuracy stats in live trading context.
     */
    private static void printLiveSummary() {
        int total = totalPredictions + skippedPredictions;
        double acc = totalPredictions == 0 ? 0.0 : ((double) correctPredictions / totalPredictions) * 100;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("timestamp", Instant.now().toEpochMilli());
        summary.put("samples", labeledSamples.size());
        summary.put("total_predictions", totalPredictions);
        summary.put("skipped", skippedPredictions);
        summary.put("correct", correctPredictions);
        summary.put("accuracy", acc);

        File logFile = new File(TRAINING_DATA_DIR + "model_summary_log.jsonl");
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(new ObjectMapper().writeValueAsString(summary));
            bw.newLine();
        } catch (IOException e) {
            System.err.println("❌ Failed to write summary log: " + e.getMessage());
        }

        // Optional: still print to console
        System.out.println("\n📊 === Live StrategyOne Summary ===");
        summary.forEach((k, v) -> System.out.printf("%s: %s%n", k, v));
    }

    /**
     * Loads existing model and ARFF header for inference.
     */
    private static void loadModel() {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(MODEL_DIR + STRATEGY + ".model"))) {

            Classifier clf = (Classifier) ois.readObject();
            Instances hdr = new Instances(
                    new BufferedReader(new FileReader(MODEL_DIR + STRATEGY + "_header.arff"))
            );
            hdr.setClassIndex(hdr.numAttributes() - 1);

            modelMap.put(STRATEGY, clf);
            headerMap.put(STRATEGY, hdr);

            System.out.println("✅ Loaded model and header for: " + STRATEGY);
        } catch (Exception e) {
            System.err.println("⚠️ Could not load model/header for " + STRATEGY + ": " + e.getMessage());
        }
    }

    /**
     * Represents a pending decision being monitored in live ticks.
     */
    private static class PendingDecision {
        final long timestamp;
        final double feature;
        final float entryPrice;
        final Signal direction;
        final int symbolId; // NEW
        int tickCount;

        PendingDecision(long timestamp, double feature, float entryPrice, Signal direction, int tickCount, int symbolId) {
            this.timestamp = timestamp;
            this.feature = feature;
            this.entryPrice = entryPrice;
            this.direction = direction;
            this.tickCount = tickCount;
            this.symbolId = symbolId;
        }

        public double[] featureVector() {
            return new double[]{feature, 0, 0};
        }
    }


    /**
     * Training data unit (features + label).
     */
    private static class LabeledSample {
        final double[] features;
        final String label;

        LabeledSample(double[] features, String label) {
            this.features = features;
            this.label = label;
        }
    }
}
