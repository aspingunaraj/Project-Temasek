package org.example.tradeGovernance.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NormalOrderRequest {

    // 🛒 Transaction type: "B" (Buy) or "S" (Sell)
    @JsonProperty("txn_type")
    private String txnType;

    // 📈 Exchange: "NSE" or "BSE"
    private String exchange;

    // 🔁 Segment: "E" (Equity), "D" (Derivatives)
    private String segment;

    // 🛠 Product type: C, I, M, B, V
    private String product;

    // 🆔 Security ID of scrip
    @JsonProperty("security_id")
    private String securityId;

    // 🔢 Quantity of stocks
    private int quantity;

    // 🕒 Validity: "DAY", "IOC", "GTC", "GTD"
    private String validity;

    // 📌 Order type: "LMT", "MKT", "SL", "SLM"
    @JsonProperty("order_type")
    private String orderType;

    // 💰 Price (used for LMT orders)
    private double price;

    // 🚨 Trigger price (used for SL or SLM orders)
    @JsonProperty("trigger_price")
    private Double triggerPrice; // Optional, so use wrapper class

    // 🌐 Source of order: "W", "M", "N", "I", "R", etc.
    private String source;

    // 🌙 After market flag: "true" for AMO, "false" for live
    @JsonProperty("off_mkt_flag")
    private String offMktFlag;

    // --- Constructor ---
    public NormalOrderRequest(String txnType, String exchange, String segment, String product,
                              String securityId, int quantity, String validity, String orderType,
                              double price, Double triggerPrice, String source, String offMktFlag) {
        this.txnType = txnType;
        this.exchange = exchange;
        this.segment = segment;
        this.product = product;
        this.securityId = securityId;
        this.quantity = quantity;
        this.validity = validity;
        this.orderType = orderType;
        this.price = price;
        this.triggerPrice = triggerPrice;
        this.source = source;
        this.offMktFlag = offMktFlag;
    }

    // --- Getters and Setters ---

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

    public String getSecurityId() {
        return securityId;
    }

    public void setSecurityId(String securityId) {
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

    public Double getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(Double triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getOffMktFlag() {
        return offMktFlag;
    }

    public void setOffMktFlag(String offMktFlag) {
        this.offMktFlag = offMktFlag;
    }
}
