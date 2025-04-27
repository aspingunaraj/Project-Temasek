package org.example.dataAnalysis.depthStrategy.machineLearning.backTesting;

import org.example.websocket.model.Tick;
import weka.classifiers.Classifier;
import weka.core.*;

import java.util.*;

public class MLUtils {

    private final ModelSelector.ModelType modelType;

    public MLUtils(ModelSelector.ModelType modelType) {
        this.modelType = modelType;
    }

    public Instances createDataset(List<double[]> featureList, List<String> labelList) {
        ArrayList<Attribute> attributes = new ArrayList<>();

        for (int i = 0; i < featureList.get(0).length; i++) {
            attributes.add(new Attribute("f" + i));
        }

        ArrayList<String> classValues = new ArrayList<>(Arrays.asList("BUY", "SELL", "HOLD"));
        attributes.add(new Attribute("class", classValues));

        Instances dataset = new Instances("TickData", attributes, featureList.size());
        dataset.setClassIndex(attributes.size() - 1);

        for (int i = 0; i < featureList.size(); i++) {
            double[] vals = new double[attributes.size()];
            System.arraycopy(featureList.get(i), 0, vals, 0, featureList.get(i).length);
            vals[vals.length - 1] = classValues.indexOf(labelList.get(i));
            dataset.add(new DenseInstance(1.0, vals));
        }

        return dataset;
    }

    public Classifier trainModel(List<double[]> featureList, List<String> labelList) throws Exception {
        Instances dataset = createDataset(featureList, labelList);
        Classifier model = ModelSelector.getModel(modelType);
        model.buildClassifier(dataset);
        return model;
    }

    public PredictionResult predictWithConfidence(Classifier model, double[] features) throws Exception {
        Instances dataset = createEmptyDataset(features.length);
        Instance instance = new DenseInstance(1.0, features);
        instance.setDataset(dataset);

        double[] distribution = model.distributionForInstance(instance);
        int bestIndex = 0;
        for (int i = 1; i < distribution.length; i++) {
            if (distribution[i] > distribution[bestIndex]) {
                bestIndex = i;
            }
        }

        String predictedLabel = dataset.classAttribute().value(bestIndex);
        double confidence = distribution[bestIndex];

        return new PredictionResult(predictedLabel, confidence);
    }

    private Instances createEmptyDataset(int featureLength) {
        ArrayList<Attribute> attributes = new ArrayList<>();
        for (int i = 0; i < featureLength; i++) {
            attributes.add(new Attribute("f" + i));
        }
        ArrayList<String> classValues = new ArrayList<>(Arrays.asList("BUY", "SELL", "HOLD"));
        attributes.add(new Attribute("class", classValues));
        Instances dataset = new Instances("TickData", attributes, 0);
        dataset.setClassIndex(attributes.size() - 1);
        return dataset;
    }

    public double[] extractFeatures(Tick tick) {
        double ltp = tick.getLastTradedPrice();
        double spread = tick.getMbpRowPacket().get(0).getSellPrice() - tick.getMbpRowPacket().get(0).getBuyPrice();
        double buyQty = tick.getTotalBuyQuantity();
        double sellQty = tick.getTotalSellQuantity();

        return new double[] {
                ltp,
                spread,
                buyQty,
                sellQty,
                tick.getHigh() - tick.getLow(),
                tick.getOpen() - tick.getClose(),
                tick.getVolumeTraded()
        };
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
