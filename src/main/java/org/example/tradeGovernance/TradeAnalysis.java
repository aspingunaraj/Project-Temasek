package org.example.tradeGovernance;

import org.example.Main;
import org.example.dataAnalysis.DepthPacketStrategies;
import org.example.tradeGovernance.model.BracketOrderRequest;
import org.example.tradeGovernance.model.Position;
import org.springframework.core.annotation.Order;

import java.util.List;

public class TradeAnalysis {

    private final PositionServices positionServices;

    public TradeAnalysis() {
        this.positionServices = new PositionServices(); // You must define this class separately
    }

    public enum Action {
        BUY,   // Take a fresh buy position
        SELL,  // Take a fresh sell position
        EXIT,  // Close an existing position
        HOLD   // Maintain current state
    }

    public Action evaluateTradeAction(Integer securityId, DepthPacketStrategies.Signal signal, String jwtToken) {
        List<Position> positions = Main.currentPositions;

        if (positions == null || positions.isEmpty()) {
            return signalToAction(signal);
        }

        for (Position position : positions) {
            if (position.getSecurity_id().equals(securityId)) {
                int netQty = position.getNet_qty();

                if (signal == DepthPacketStrategies.Signal.BUY) {
                    if (netQty < 0) return Action.EXIT;
                    if (netQty == 0) return Action.BUY;
                    return Action.HOLD;
                }

                if (signal == DepthPacketStrategies.Signal.SELL) {
                    if (netQty > 0) return Action.EXIT;
                    if (netQty == 0) return Action.SELL;
                    return Action.HOLD;
                }

                return Action.HOLD;
            }
        }

        return signalToAction(signal);
    }

    private Action signalToAction(DepthPacketStrategies.Signal signal) {
        return switch (signal) {
            case BUY -> Action.BUY;
            case SELL -> Action.SELL;
            case HOLD -> Action.HOLD;
        };
    }

    /**
     * If the action is BUY or SELL, create and place a bracket order
     * by delegating to the external OrderExecutionService.
     */

}
