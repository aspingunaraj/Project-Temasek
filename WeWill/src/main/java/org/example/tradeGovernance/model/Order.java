package org.example.tradeGovernance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Order {

    @JsonProperty("client_id") private String clientId;
    @JsonProperty("txn_type") private String txnType;
    private String exchange;
    private String segment;
    private String product;
    @JsonProperty("security_id") private String securityId;
    private int quantity;
    private String validity;
    @JsonProperty("order_type") private String orderType;
    private double price;
    @JsonProperty("off_mkt_flag") private String offMktFlag;
    @JsonProperty("mkt_type") private String mktType;
    @JsonProperty("order_no") private String orderNo;
    @JsonProperty("serial_no") private int serialNo;
    @JsonProperty("group_id") private int groupId;
    @JsonProperty("leg_no") private String legNo;
    @JsonProperty("algo_ord_no") private String algoOrdNo;
    @JsonProperty("trigger_price") private double triggerPrice;
    private String status;
    @JsonProperty("exch_order_no") private String exchOrderNo;
    @JsonProperty("exch_order_time") private String exchOrderTime;
    @JsonProperty("traded_qty") private int tradedQty;
    @JsonProperty("remaining_quantity") private int remainingQuantity;
    @JsonProperty("avg_traded_price") private double avgTradedPrice;
    @JsonProperty("reason_description") private String reasonDescription;
    @JsonProperty("pr_abstick_value") private String prAbstickValue;
    @JsonProperty("sl_abstick_value") private String slAbstickValue;
    private String isin;
    @JsonProperty("display_name") private String displayName;
    @JsonProperty("order_date_time") private String orderDateTime;
    @JsonProperty("last_updated_time") private String lastUpdatedTime;
    @JsonProperty("child_leg_unq_id") private long childLegUnqId;
    @JsonProperty("ref_ltp") private double refLtp;
    @JsonProperty("display_status") private String displayStatus;
    @JsonProperty("display_product") private String displayProduct;
    @JsonProperty("display_order_type") private String displayOrderType;
    @JsonProperty("display_validity") private String displayValidity;
    @JsonProperty("error_code") private String errorCode;
    @JsonProperty("tick_size") private double tickSize;
    @JsonProperty("strategy_id") private String strategyId;
    @JsonProperty("placed_by") private String placedBy;
    @JsonProperty("lot_size") private int lotSize;
    @JsonProperty("strike_price") private double strikePrice;
    @JsonProperty("expiry_date") private String expiryDate;
    @JsonProperty("opt_type") private String optType;
    private String instrument;
    private String platform;
    private String channel;
    @JsonProperty("instrument_type") private String instrumentType;
    @JsonProperty("tag_type") private String tagType;
    @JsonProperty("algo_module") private String algoModule;
    @JsonProperty("tag_id") private String tagId;
}
