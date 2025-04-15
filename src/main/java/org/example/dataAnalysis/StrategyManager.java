package org.example.dataAnalysis;

import org.example.dataAnalysis.depthStrategy.StrategyOne;
import org.example.websocket.model.Tick;

import java.time.*;
import java.util.List;

public class StrategyManager {

    /**
     * Selects and runs the per-strategy ML voting strategy based on market hours.
     *
     * @param recentTicks List of recent ticks for a symbol
     * @param symbolId    ID of the symbol being evaluated
     * @return Signal (BUY/SELL/HOLD) from the chosen strategy
     */
    public static StrategyOne.Signal strategySelector(List<Tick> recentTicks, int symbolId) {
        if (recentTicks == null || recentTicks.isEmpty()) return StrategyOne.Signal.HOLD;

        // Step 1: Take the latest tick for signal evaluation
        Tick latestTick = recentTicks.get(recentTicks.size() - 1);

        // Step 2: Evaluate signal from StrategyOne (BUY/SELL/HOLD)
        StrategyOne.Signal signal = StrategyOne.evaluateSignalFusion(latestTick);

        // Step 3: Always track outcomes (to log TP/SL for past trades)
        StrategyOne.evaluateOutcomes(latestTick);

        return signal;
    }
}
