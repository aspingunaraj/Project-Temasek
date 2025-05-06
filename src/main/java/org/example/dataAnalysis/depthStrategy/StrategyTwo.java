package org.example.dataAnalysis.depthStrategy;

import org.example.websocket.model.Tick;
import weka.classifiers.Classifier;
import weka.core.SerializationHelper;
import org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.MLUtils;
import org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.ModelSelector;

import java.util.HashMap;
import java.util.Map;

/**
 * StrategyTwo (Simplified) - Only performs inference using pre-trained models.
 * Expects already-aggregated ticks. No training, no label finalization.
 */
public class StrategyTwo {

    private static final String MODEL_DIR = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/models/";
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.6;

    private final Map<Integer, Classifier> modelMap = new HashMap<>();
    private final MLUtils mlUtils = new MLUtils(ModelSelector.ModelType.RANDOM_FOREST);

    /**
     * Evaluates the signal using the pre-trained model for the symbol.
     *
     * @param tick Aggregated tick (input to the model)
     * @return Trading signal (BUY, SELL, HOLD)
     * @throws Exception if model not found or prediction fails
     */
    public StrategyOne.Signal evaluateSignal(Tick tick) throws Exception {
        int symbolId = tick.getSecurityId();

        // Load model if not already loaded
        if (!modelMap.containsKey(symbolId)) {
            String modelPath = MODEL_DIR + "model_" + symbolId + ".model";
            Classifier model = (Classifier) SerializationHelper.read(modelPath);
            modelMap.put(symbolId, model);
        }

        // Extract features and predict
        double[] features = mlUtils.extractFeatures(tick);
        MLUtils.PredictionResult result = mlUtils.predictWithConfidence(modelMap.get(symbolId), features);

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
