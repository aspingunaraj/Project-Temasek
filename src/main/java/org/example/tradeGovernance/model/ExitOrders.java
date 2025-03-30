package org.example.tradeGovernance.model;

public class ExitOrders {
    private Order targetOrder;
    private Order stopLossOrder;

    public ExitOrders(Order targetOrder, Order stopLossOrder) {
        this.targetOrder = targetOrder;
        this.stopLossOrder = stopLossOrder;
    }

    public Order getTargetOrder() {
        return targetOrder;
    }

    public Order getStopLossOrder() {
        return stopLossOrder;
    }
}
