package org.example.dataAnalysis;

import org.example.dataAnalysis.depthStrategy.StrategyOne;
import org.example.dataAnalysis.depthStrategy.StrategyTwo;
import org.example.websocket.model.Tick;

import java.util.*;

import static org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.BackTesterUtility.*;

public class StrategyManager {

    private static final int AGGREGATION_WINDOW = 1;
    private static final Map<Integer, List<Tick>> tickBufferMap = new HashMap<>();

    // Create a single instance of StrategyTwo
    private static final StrategyTwo strategyTwo = new StrategyTwo();

    public static StrategyOne.Signal strategySelector(Tick tick, int symbolId) throws Exception {
        if (tick == null) return StrategyOne.Signal.HOLD;

        tickBufferMap.computeIfAbsent(symbolId, k -> new ArrayList<>()).add(tick);
        List<Tick> buffer = tickBufferMap.get(symbolId);

        System.out.println("buffer size " + buffer.size());
        if (buffer.size() < AGGREGATION_WINDOW) {
            return StrategyOne.Signal.HOLD;
        }

        Tick aggregatedTick = aggregateTicks(buffer);
        tickBufferMap.put(symbolId, new ArrayList<>());
        System.out.println("evaluate Signal entered");

        // Use instance method
        return strategyTwo.evaluateSignal(aggregatedTick);
    }
}
