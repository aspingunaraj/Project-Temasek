package org.example.tradeGovernance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class OrderCancelResponse {

    private String status;
    private String message;

    @JsonProperty("error_code")
    private String errorCode;

    private List<OrderCancelData> data;

    @Data
    public static class OrderCancelData {
        @JsonProperty("oms_error_code")
        private String omsErrorCode;

        @JsonProperty("order_no")
        private String orderNo;
    }
}
