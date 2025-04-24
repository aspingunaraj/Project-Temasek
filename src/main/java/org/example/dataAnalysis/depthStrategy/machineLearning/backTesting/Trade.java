package org.example.dataAnalysis.depthStrategy.machineLearning.backTesting;

public class Trade {
    public long entryTime;
    public long exitTime;
    public double entryPrice;
    public double exitPrice;
    public double entryImbalance;
    public String exitReason;
    public double pnl;
    public String entryType; // "Buy" or "Sell"

    public Trade(long entryTime, long exitTime, double entryPrice, double exitPrice,
                 double entryImbalance, String exitReason, double pnl, String entryType) {
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.entryPrice = entryPrice;
        this.exitPrice = exitPrice;
        this.entryImbalance = entryImbalance;
        this.exitReason = exitReason;
        this.pnl = pnl;
        this.entryType = entryType;
    }
}
