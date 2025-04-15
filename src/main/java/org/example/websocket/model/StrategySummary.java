package org.example.websocket.model;


public class StrategySummary {

    private String strategy;
    private String signal;
    private int success;
    private int failure;
    private int total;
    private String successRate;

    // Getter and Setter for strategy
    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    // Getter and Setter for signal
    public String getSignal() {
        return signal;
    }

    public void setSignal(String signal) {
        this.signal = signal;
    }

    // Getter and Setter for success
    public int getSuccess() {
        return success;
    }

    public void setSuccess(int success) {
        this.success = success;
    }

    // Getter and Setter for failure
    public int getFailure() {
        return failure;
    }

    public void setFailure(int failure) {
        this.failure = failure;
    }

    // Getter and Setter for total
    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    // Getter and Setter for successRate
    public String getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(String successRate) {
        this.successRate = successRate;
    }
}
