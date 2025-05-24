package org.example.dataAnalysis.depthStrategy;

import org.example.websocket.model.Tick;
import weka.classifiers.Classifier;
import weka.core.SerializationHelper;
import org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.MLUtils;
import org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.ModelSelector;

/**
 * StrategyTwo (Simplified) - Performs inference using a single global pre-trained model.
 * Expects already-aggregated ticks. No training, no label finalization.
 */
public class StrategyTwo {

    private static final String MODEL_PATH = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/models/model_global.model";
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.5;

    private final MLUtils mlUtils = new MLUtils(ModelSelector.ModelType.RANDOM_FOREST);
    private Classifier globalModel;

    /**
     * Evaluates the signal using a shared global model.
     *
     * @param tick Aggregated tick (input to the model)
     * @return Trading signal (BUY, SELL, HOLD)
     * @throws Exception if model loading or prediction fails
     */
    public StrategyOne.Signal evaluateSignal(Tick tick) throws Exception {
        // Load model once
        if (globalModel == null) {
            globalModel = (Classifier) SerializationHelper.read(MODEL_PATH);
        }

        // Extract features and predict
        double[] features = mlUtils.extractFeatures(tick);
        MLUtils.PredictionResult result = mlUtils.predictWithConfidence(globalModel, features);

        if (result.confidence < MIN_CONFIDENCE_THRESHOLD) {
            return StrategyOne.Signal.HOLD;
        }

        return switch (result.label) {
            case "BUY" -> StrategyOne.Signal.BUY;
            case "SELL" -> StrategyOne.Signal.SELL;
            default -> StrategyOne.Signal.HOLD;
        };
    }
}
