package org.example.dataAnalysis.machineLearning;



import org.example.websocket.model.DepthPacket;
import org.example.websocket.model.Tick;
import org.tribuo.*;
import org.tribuo.classification.Label;
import org.tribuo.impl.ListExample;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.nio.file.Paths;
import java.util.List;

public class Predictor {
    public static void main(String[] args) {
        try {
            // Load model using Java serialization
            Model<Label> model;
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream("src/main/java/org/example/dataAnalysis/machineLearning/signal-model-v1.ser"))) {
                model = (Model<Label>) ois.readObject();
                System.out.println("Model loaded successfully.");
            }

            // Create a live tick input
            Tick liveTick = Tick.builder()
                    .lastTradedPrice(101.3f)
                    .volumeTraded(7000)
                    .totalBuyQuantity(1500)
                    .totalSellQuantity(1300)
                    .mbpRowPacket(List.of(
                            new DepthPacket(0, 100, 120, (short) 0, (short) 0, 101.0f, 101.6f)
                    ))
                    .build();

            float imbalance = (float) (liveTick.getTotalBuyQuantity() - liveTick.getTotalSellQuantity()) /
                    (liveTick.getTotalBuyQuantity() + liveTick.getTotalSellQuantity() + 1e-6f);
            float spread = liveTick.getMbpRowPacket().get(0).getSellPrice() - liveTick.getMbpRowPacket().get(0).getBuyPrice();

            Example<Label> input = new ListExample<>(new Label("UNKNOWN"), List.of(
                    new Feature("ltp", liveTick.getLastTradedPrice()),
                    new Feature("volume", liveTick.getVolumeTraded()),
                    new Feature("buyQty", liveTick.getTotalBuyQuantity()),
                    new Feature("sellQty", liveTick.getTotalSellQuantity()),
                    new Feature("orderFlowImbalance", imbalance),
                    new Feature("spread", spread)
            ));

            // Predict
            Prediction<Label> prediction = model.predict(input);
            System.out.println("Predicted Signal: " + prediction.getOutput().getLabel());
            System.out.println("Confidence Scores: " + prediction.getOutputScores());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
