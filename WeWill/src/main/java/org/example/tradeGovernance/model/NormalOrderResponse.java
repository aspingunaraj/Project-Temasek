package org.example.tradeGovernance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class NormalOrderResponse {

    private String status;
    private String message;

    @JsonProperty("data")
    private List<OrderData> data;

    @JsonProperty("error_code")
    private String errorCode;

    // --- Getters and Setters ---

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<OrderData> getData() {
        return data;
    }

    public void setData(List<OrderData> data) {
        this.data = data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    // --- Inner class for data array ---
    public static class OrderData {

        @JsonProperty("order_no")
        private String orderNo;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }
    }
}
