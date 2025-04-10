package org.example.dataAnalysis.depthStrategy;

import lombok.Data;
import org.example.dataAnalysis.depthStrategy.StrategyOne;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks outcomes of signals per symbol using fixed target profit and stop-loss thresholds.
 * Evaluates each signal's result based on LTP movement after signal generation.
 */
public class SignalOutcomeTracker {

    // === Configuration Thresholds ===
    private static final double TARGET_PROFIT_PCT = 1.0; // +1%
    private static final double STOP_LOSS_PCT = 0.5;      // -0.5%

    // === In-Memory Data Stores ===
    private static final Map<Integer, SignalOutcome> openSignals = new ConcurrentHashMap<>();
    private static final List<SignalOutcome> completedSignals = Collections.synchronizedList(new ArrayList<>());

    /**
     * Registers a new signal for a symbol with entry price and signal type.
     */
    public static void registerSignal(int symbolId, double entryPrice, StrategyOne.Signal signalType) {
        SignalOutcome outcome = new SignalOutcome(
                symbolId,
                signalType,
                entryPrice,
                Instant.now(),
                computeTarget(entryPrice, signalType),
                computeStopLoss(entryPrice, signalType)
        );
        openSignals.put(symbolId, outcome);
    }

    /**
     * Evaluates current price against open signal's target or SL.
     * If either is hit, logs and finalizes the signal.
     */
    public static void evaluate(int symbolId, double currentPrice) {
        if (!openSignals.containsKey(symbolId)) return;

        SignalOutcome outcome = openSignals.get(symbolId);

        boolean hitTarget = false;
        boolean hitStop = false;

        if (outcome.getSignalType() == StrategyOne.Signal.BUY) {
            hitTarget = currentPrice >= outcome.getTargetPrice();
            hitStop = currentPrice <= outcome.getStopLossPrice();
        } else if (outcome.getSignalType() == StrategyOne.Signal.SELL) {
            hitTarget = currentPrice <= outcome.getTargetPrice();
            hitStop = currentPrice >= outcome.getStopLossPrice();
        }

        if (hitTarget || hitStop) {
            outcome.setExitPrice(currentPrice);
            outcome.setExitTime(Instant.now());
            outcome.setOutcome(hitTarget ? "SUCCESS" : "FAILURE");

            completedSignals.add(outcome);
            openSignals.remove(symbolId);
        }
    }

    /**
     * Returns list of all completed signal outcomes.
     */
    public static List<SignalOutcome> getCompletedSignals() {
        return new ArrayList<>(completedSignals);
    }

    /**
     * Returns the currently open signal for the given symbol (if any).
     */
    public static Optional<SignalOutcome> getOpenSignal(int symbolId) {
        return Optional.ofNullable(openSignals.get(symbolId));
    }

    /**
     * Clears all signals and outcomes.
     */
    public static void clearAll() {
        openSignals.clear();
        completedSignals.clear();
    }

    /**
     * Calculates the target price for a given signal type and entry.
     */
    public static double computeTarget(double entry, StrategyOne.Signal signalType) {
        return signalType == StrategyOne.Signal.BUY
                ? entry * (1 + TARGET_PROFIT_PCT / 100.0)
                : entry * (1 - TARGET_PROFIT_PCT / 100.0);
    }

    /**
     * Calculates the stop-loss price for a given signal type and entry.
     */
    public static double computeStopLoss(double entry, StrategyOne.Signal signalType) {
        return signalType == StrategyOne.Signal.BUY
                ? entry * (1 - STOP_LOSS_PCT / 100.0)
                : entry * (1 + STOP_LOSS_PCT / 100.0);
    }

    // === Data Holder Class ===

    @Data
    public static class SignalOutcome {
        private int symbolId;
        private StrategyOne.Signal signalType;
        private double entryPrice;
        private Instant entryTime;

        private double targetPrice;
        private double stopLossPrice;

        private double exitPrice;
        private Instant exitTime;
        private String outcome; // "SUCCESS", "FAILURE", or null if pending

        // Constructor used during signal registration (entry-side only)
        public SignalOutcome(int symbolId, StrategyOne.Signal signalType,
                             double entryPrice, Instant entryTime,
                             double targetPrice, double stopLossPrice) {
            this.symbolId = symbolId;
            this.signalType = signalType;
            this.entryPrice = entryPrice;
            this.entryTime = entryTime;
            this.targetPrice = targetPrice;
            this.stopLossPrice = stopLossPrice;

            this.exitPrice = 0.0;
            this.exitTime = null;
            this.outcome = null;
        }

        // ✅ Full constructor (used for testing or loading from logs)
        public SignalOutcome(int symbolId, StrategyOne.Signal signalType,
                             double entryPrice, Instant entryTime,
                             double targetPrice, double stopLossPrice,
                             double exitPrice, Instant exitTime, String outcome) {
            this.symbolId = symbolId;
            this.signalType = signalType;
            this.entryPrice = entryPrice;
            this.entryTime = entryTime;
            this.targetPrice = targetPrice;
            this.stopLossPrice = stopLossPrice;
            this.exitPrice = exitPrice;
            this.exitTime = exitTime;
            this.outcome = outcome;
        }

        // Default constructor (for serialization, etc.)
        public SignalOutcome() {}
    }
}
