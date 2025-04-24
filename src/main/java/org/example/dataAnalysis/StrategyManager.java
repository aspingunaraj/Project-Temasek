package org.example.dataAnalysis;

import org.example.dataAnalysis.depthStrategy.StrategyOne;
import org.example.websocket.model.Tick;

import java.util.*;
import static org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.BackTesterUtility.*;

public class StrategyManager {

    // Number of ticks to accumulate before evaluating the signal
    private static final int AGGREGATION_WINDOW = 10;

    // Buffer to store ticks per symbol. Each symbol maintains its own rolling tick list
    private static final Map<Integer, List<Tick>> tickBufferMap = new HashMap<>();

    /**
     * Receives a single tick and accumulates it into a buffer specific to the symbolId.
     * Once the buffer reaches the AGGREGATION_WINDOW size (e.g., 10 ticks),
     * it aggregates them into a single representative Tick and evaluates the trading signal.
     * After evaluation, the buffer for that symbol is cleared to start a new cycle.
     *
     * @param tick      The latest tick received for a symbol
     * @param symbolId  The unique ID representing the security/symbol
     * @return Signal (BUY/SELL/HOLD) based on the aggregated tick data
     */
    public static StrategyOne.Signal strategySelector(Tick tick, int symbolId) {
        if (tick == null) return StrategyOne.Signal.HOLD;

        // Add the incoming tick to the buffer for this symbol
        tickBufferMap.computeIfAbsent(symbolId, k -> new ArrayList<>()).add(tick);
        List<Tick> buffer = tickBufferMap.get(symbolId);

        // If the buffer has not yet reached the required size, return HOLD
        if (buffer.size() < AGGREGATION_WINDOW) {
            return StrategyOne.Signal.HOLD;
        }

        // Aggregate the buffered ticks into a single Tick object
        Tick aggregatedTick = aggregateTicks(buffer);

        // Reset the buffer for this symbol to begin collecting the next set
        tickBufferMap.put(symbolId, new ArrayList<>());

        // Evaluate and return the signal using the aggregated Tick
        return StrategyOne.evaluateSignal(aggregatedTick);
    }
}
