package org.example.dataAnalysis;

import org.example.websocket.model.Tick;

import java.time.*;
import java.util.List;

public class StrategyManager {

    public enum StrategyType {
        DEPTH_PACKET,
        // Add more strategies later if needed
    }

    /**
     * Selects and runs the appropriate strategy based on the current time and other conditions.
     *
     * @param recentTicks List of recent ticks for a symbol
     * @param symbolId    ID of the symbol being evaluated
     * @return Signal (BUY/SELL/HOLD) from the chosen strategy
     */
    public static StrategyOne.Signal strategySelector(List<Tick> recentTicks, int symbolId) {
        // 🕘 Time-based strategy gating: Run DepthPacketStrategies only from 9:00 to 10:00 AM IST
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        LocalTime currentTime = nowIST.toLocalTime();

        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(10, 0);

        if (!currentTime.isBefore(start) && currentTime.isBefore(end)) {
            System.out.println("🧠 Using DepthPacketStrategies for symbol: " + symbolId);
            return StrategyOne.evaluateMarketSignal(recentTicks);
        } else {
            System.out.println("🕒 Outside 9–10 AM IST window. Skipping strategy evaluation for symbol: " + symbolId);
            return StrategyOne.Signal.HOLD;
        }
    }
}
