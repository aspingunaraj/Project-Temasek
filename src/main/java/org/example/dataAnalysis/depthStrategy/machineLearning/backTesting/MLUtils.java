package org.example.dataAnalysis.depthStrategy.machineLearning.backTesting;

import org.example.websocket.model.DepthPacket;
import org.example.websocket.model.Tick;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.*;

import java.util.*;

public class MLUtils {

    private final ModelSelector.ModelType modelType;

    public MLUtils(ModelSelector.ModelType modelType) {
        this.modelType = modelType;
    }

    // ----- Feature Extraction -----
    public double[] extractFeatures(Tick tick) {
        double ltp = tick.getLastTradedPrice();
        int totalBuyQty = tick.getTotalBuyQuantity();
        int totalSellQty = tick.getTotalSellQuantity();

        // Order Book Imbalance (OBI)
        double obi = (double)(totalBuyQty - totalSellQty) / (totalBuyQty + totalSellQty + 1e-6);

        // Spread at top of book
        List<DepthPacket> depthList = tick.getMbpRowPacket();
        double bestBuyPrice = depthList.get(0).getBuyPrice();
        double bestSellPrice = depthList.get(0).getSellPrice();
        double spread = bestSellPrice - bestBuyPrice;

        // Depth skew and pressure (weighted by level)
        double buyVolume = 0, sellVolume = 0;
        double weightedBuy = 0, weightedSell = 0;
        int level = 1;

        for (DepthPacket dp : depthList) {
            buyVolume += dp.getBuyQuantity();
            sellVolume += dp.getSellQuantity();

            weightedBuy += dp.getBuyQuantity() / (double) level;
            weightedSell += dp.getSellQuantity() / (double) level;

            level++;
        }

        double skew = (buyVolume - sellVolume) / (buyVolume + sellVolume + 1e-6);
        double pressure = weightedBuy / (weightedBuy + weightedSell + 1e-6);

        return new double[] {
                ltp,
                obi,
                spread,
                skew,
                pressure
        };
    }

    // ----- Model Training -----
    public Classifier trainModel(List<double[]> features, List<String> labels) throws Exception {
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("ltp"));
        attributes.add(new Attribute("obi"));
        attributes.add(new Attribute("spread"));
        attributes.add(new Attribute("skew"));
        attributes.add(new Attribute("pressure"));

        List<String> classValues = Arrays.asList("BUY", "SELL", "HOLD");
        attributes.add(new Attribute("label", classValues));

        Instances dataset = new Instances("TickData", attributes, features.size());
        dataset.setClassIndex(attributes.size() - 1);

        for (int i = 0; i < features.size(); i++) {
            double[] instanceValues = Arrays.copyOf(features.get(i), features.get(i).length + 1);
            instanceValues[instanceValues.length - 1] = classValues.indexOf(labels.get(i));
            dataset.add(new DenseInstance(1.0, instanceValues));
        }

        Classifier classifier = (modelType == ModelSelector.ModelType.RANDOM_FOREST)
                ? new RandomForest()
                : ModelSelector.getModel(modelType);

        classifier.buildClassifier(dataset);
        return classifier;
    }

    // ----- Prediction with Confidence -----
    public PredictionResult predictWithConfidence(Classifier model, double[] features) throws Exception {
        Instances dummy = createEmptyDataset();
        Instance instance = new DenseInstance(1.0, Arrays.copyOf(features, features.length + 1));
        dummy.add(instance);
        dummy.setClassIndex(dummy.numAttributes() - 1);

        double[] distribution = model.distributionForInstance(dummy.firstInstance());
        int bestIndex = 0;
        for (int i = 1; i < distribution.length; i++) {
            if (distribution[i] > distribution[bestIndex]) {
                bestIndex = i;
            }
        }

        String predictedLabel = dummy.classAttribute().value(bestIndex);
        double confidence = distribution[bestIndex];

        return new PredictionResult(predictedLabel, confidence);
    }

    private Instances createEmptyDataset() {
        ArrayList<Attribute> attributes = new ArrayList<>();
        attributes.add(new Attribute("ltp"));
        attributes.add(new Attribute("obi"));
        attributes.add(new Attribute("spread"));
        attributes.add(new Attribute("skew"));
        attributes.add(new Attribute("pressure"));
        List<String> classValues = Arrays.asList("BUY", "SELL", "HOLD");
        attributes.add(new Attribute("label", classValues));
        return new Instances("TickData", attributes, 0);
    }

    public static class PredictionResult {
        public final String label;
        public final double confidence;

        public PredictionResult(String label, double confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }
}
