package org.example.dataAnalysis.depthStrategy.machineLearning.backTesting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * BackTester implements progressive ML on tick data.
 * It delays training for 5000 ticks (soak-in), retrains every 1000 ticks, and applies a confidence threshold.
 */
public class BackTester {

    private static final String DATA_DIR   = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/";
    private static final String INPUT_FILE = DATA_DIR + "compressedTickDump.json";

    private static final double TARGET_PROFIT = 0.002; // +0.2%
    private static final double STOP_LOSS     = 0.002; // -0.2%
    private static final double CONFIDENCE_THRESHOLD = 0.999;

    private static final int SOAK_IN_SIZE     = 5000;
    private static final int RETRAIN_INTERVAL = 1000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        List<Sample> historical = new ArrayList<>();
        List<JsonNode> rawTicks = loadJsonLines(INPUT_FILE);

        Classifier model = null;
        List<String> predictions = new ArrayList<>();
        List<String> batchPredictions = new ArrayList<>();
        int skippedTotal = 0;
        int skippedBatch = 0;

        for (int i = 0; i < rawTicks.size(); i++) {
            JsonNode node = rawTicks.get(i);
            Sample sample = extractSample(node, rawTicks, i);
            if (sample == null) continue;

            historical.add(sample);

            // Retrain model progressively
            boolean shouldRetrain = historical.size() >= SOAK_IN_SIZE &&
                    (historical.size() - SOAK_IN_SIZE) % RETRAIN_INTERVAL == 0;

            if (shouldRetrain) {
                model = trainModel(historical);
                System.out.printf("\n🧠 Retrained model at tick %d%n", i);
            }

            if (model != null) {
                Instances singleton = createInstances(Collections.singletonList(sample), "PredictOne");
                Instance inst = singleton.instance(0);
                double predictedIdx = model.classifyInstance(inst);
                double[] dist = model.distributionForInstance(inst);
                double confidence = dist[(int) predictedIdx];

                if (confidence >= CONFIDENCE_THRESHOLD) {
                    String predicted = singleton.classAttribute().value((int) predictedIdx);
                    String actual = sample.label;
                    String result = predicted + "|" + actual;
                    predictions.add(result);
                    batchPredictions.add(result);
                } else {
                    skippedTotal++;
                    skippedBatch++;
                }
            }

            // Evaluate each batch of 1000 ticks
            boolean shouldEvaluate = model != null &&
                    (historical.size() - SOAK_IN_SIZE) % RETRAIN_INTERVAL == 0 &&
                    !batchPredictions.isEmpty();

            if (shouldEvaluate) {
                summarize(batchPredictions, skippedBatch, "Summary for ticks " + (i - RETRAIN_INTERVAL + 1) + " to " + i);
                batchPredictions.clear();
                skippedBatch = 0;
            }
        }

        // Final summary
        summarize(predictions, skippedTotal, "Final Overall Summary");
    }

    private static List<JsonNode> loadJsonLines(String path) throws IOException {
        List<JsonNode> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                list.add(MAPPER.readTree(line));
            }
        }
        System.out.printf("📥 Loaded %d ticks from: %s%n", list.size(), path);
        return list;
    }

    private static Sample extractSample(JsonNode node, List<JsonNode> ticks, int index) {
        double entry = node.get("lastTradedPrice").asDouble();
        int symbolId = node.get("securityId").asInt(); // 👈 Extract symbol ID

        JsonNode mbp = node.get("mbpRowPacket");
        int buyQty = 0, sellQty = 0;
        double topBuy = 0, topSell = 0;
        if (mbp != null && mbp.isArray() && mbp.size() > 0) {
            topBuy = mbp.get(0).get("buyPrice").asDouble();
            topSell = mbp.get(0).get("sellPrice").asDouble();
            for (JsonNode dp : mbp) {
                buyQty += dp.get("buyQuantity").asInt();
                sellQty += dp.get("sellQuantity").asInt();
            }
        }

        double pressure = (buyQty + sellQty == 0) ? 0.0 : (double)(buyQty - sellQty) / (buyQty + sellQty);
        double imbalance = buyQty - sellQty;
        double spread = topSell - topBuy;

        double target = entry * (1 + TARGET_PROFIT);
        double stop   = entry * (1 - STOP_LOSS);

        boolean success = false;

        // ✅ Scan ahead only for same symbol
        for (int j = index + 1; j < ticks.size(); j++) {
            JsonNode future = ticks.get(j);
            int futureSymbolId = future.get("securityId").asInt();
            if (futureSymbolId != symbolId) continue; // skip unrelated symbols

            double price = future.get("lastTradedPrice").asDouble();
            if (price >= target) {
                success = true;
                break;
            }
            if (price <= stop) {
                success = false;
                break;
            }
        }

        return new Sample(new double[]{pressure, imbalance, spread}, success ? "SUCCESS" : "FAILURE");
    }


    private static Classifier trainModel(List<Sample> data) throws Exception {
        Instances dataset = createInstances(data, "ProgressiveModel");
        RandomForest rf = new RandomForest();
        rf.buildClassifier(dataset);
        return rf;
    }

    private static Instances createInstances(List<Sample> data, String name) {
        ArrayList<Attribute> attrs = new ArrayList<>();
        attrs.add(new Attribute("pressure"));
        attrs.add(new Attribute("imbalance"));
        attrs.add(new Attribute("spread"));
        List<String> classVals = Arrays.asList("SUCCESS", "FAILURE");
        attrs.add(new Attribute("class", classVals));

        Instances dataset = new Instances(name, attrs, data.size());
        dataset.setClassIndex(attrs.size() - 1);

        for (Sample s : data) {
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
     * Generic summary printer – reusable for batches and final summary.
     */
    private static void summarize(List<String> predictions, int skipped, String header) {
        int correct = 0;
        int tp = 0, tn = 0, fp = 0, fn = 0;

        for (String result : predictions) {
            String[] parts = result.split("\\|");
            String pred = parts[0];
            String actual = parts[1];
            if (pred.equals(actual)) correct++;
            if (actual.equals("SUCCESS") && pred.equals("SUCCESS")) tp++;
            if (actual.equals("FAILURE") && pred.equals("FAILURE")) tn++;
            if (actual.equals("FAILURE") && pred.equals("SUCCESS")) fp++;
            if (actual.equals("SUCCESS") && pred.equals("FAILURE")) fn++;
        }

        double acc = predictions.isEmpty() ? 0.0 : (double) correct / predictions.size() * 100.0;

        System.out.println("\n📊 === " + header + " ===");
        System.out.printf("Evaluated Ticks    : %d%n", predictions.size());
        System.out.printf("Skipped Ticks      : %d (confidence < %.2f)%n", skipped, CONFIDENCE_THRESHOLD);
        System.out.printf("Accuracy           : %.2f%%%n", acc);
        System.out.printf("True Positives     : %d%n", tp);
        System.out.printf("True Negatives     : %d%n", tn);
        System.out.printf("False Positives    : %d%n", fp);
        System.out.printf("False Negatives    : %d%n", fn);
    }

    private static class Sample {
        final double[] features;
        final String label;
        Sample(double[] features, String label) {
            this.features = features;
            this.label = label;
        }
    }
}
