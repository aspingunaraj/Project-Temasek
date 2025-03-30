package org.example.tradeGovernance.model;


import com.fasterxml.jackson.annotation.JsonProperty;

public class BracketOrderRequest {

    private String source;       // "N"
    @JsonProperty("txn_type")
    private String txnType;      // "B"
    private String exchange;     // "BSE"
    private String segment;      // "E"
    private String product;      // "B"
    @JsonProperty("security_id")
    private int securityId;   // "500003"
    private int quantity;        // 2
    private String validity;     // "DAY"
    @JsonProperty("order_type")
    private String orderType;    // "LMT"
    private double price;        // 163
    @JsonProperty("profit_value")
    private double profitValue;  // 4
    @JsonProperty("stoploss_value")
    private double stoplossValue; // 2

    // Getters and Setters

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public int getSecurityId() {
        return securityId;
    }

    public void setSecurityId(int securityId) {
        this.securityId = securityId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getValidity() {
        return validity;
    }

    public void setValidity(String validity) {
        this.validity = validity;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getProfitValue() {
        return profitValue;
    }

    public void setProfitValue(double profitValue) {
        this.profitValue = profitValue;
    }

    public double getStoplossValue() {
        return stoplossValue;
    }

    public void setStoplossValue(double stoplossValue) {
        this.stoplossValue = stoplossValue;
    }
}
