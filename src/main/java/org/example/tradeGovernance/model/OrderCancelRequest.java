package org.example.tradeGovernance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OrderCancelRequest {

    @JsonProperty("order_no")
    private String orderNo;

    @JsonProperty("serial_no")
    private int serialNo;

    @JsonProperty("group_id")
    private int groupId;

    public OrderCancelRequest(String orderNo, int serialNo, int groupId) {
        this.orderNo = orderNo;
        this.serialNo = serialNo;
        this.groupId = groupId;
    }

    public OrderCancelRequest() {
        // Default constructor for deserialization
    }
}
