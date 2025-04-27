package org.example.dataAnalysis.depthStrategy;

import org.example.websocket.model.Tick;
import java.util.HashMap;
import java.util.Map;
import static org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.BackTesterUtility.*;

public class StrategyOne {

    public enum Signal {
        BUY, SELL, HOLD
    }

    private static final double IMBALANCE_THRESHOLD = 0.55;
    private static final Map<Integer, String> positionMap = new HashMap<>();

    /**
     * Returns a trading signal based on order book imbalance strategy logic.
     * This method is stateless with respect to trade execution and only provides directional prediction.
     */
    public static Signal evaluateSignal(Tick tick) {
        double imbalance = calculateOBImbalance(tick);
        double openPrice = tick.getOpen();
        double closePrice = tick.getClose();
        System.out.println(imbalance);

        if (imbalance >= IMBALANCE_THRESHOLD && closePrice > openPrice) {
            System.out.println("Buy Signal");
            return Signal.BUY;
        } else if (imbalance <= -IMBALANCE_THRESHOLD && openPrice > closePrice) {
            System.out.println("Sell Signal");
            return Signal.SELL;
        } else {
            System.out.println("Hold Signal");
            return Signal.HOLD;
        }
    }
}
