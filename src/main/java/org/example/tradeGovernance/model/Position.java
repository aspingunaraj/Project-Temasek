package org.example.tradeGovernance.model;

import lombok.Data;

@Data
public class Position {
    private String client_id;
    private String exchange;
    private String segment;
    private String product;
    private String security_id;
    private String mkt_type;
    private String display_name;
    private int tot_buy_qty;
    private double tot_buy_val;
    private double buy_avg;
    private int tot_sell_qty;
    private double tot_sell_val;
    private double sell_avg;
    private int net_qty;
    private double net_val;
    private double net_avg;
    private double last_traded_price;
    private double realised_profit;
    private String isin;
    private String display_pos_type;
    private String display_pos_status;
    private String display_product;
    private double tick_size;
    private int lot_size;
    private int strike_price;
    private String expiry_date;
    private String opt_type;
    private String instrument;
    private int tot_buy_qty_cf;
    private int tot_sell_qty_cf;
    private double tot_buy_val_cf;
    private double tot_sell_val_cf;
    private int tot_buy_qty_day;
    private int tot_sell_qty_day;
    private double tot_buy_val_day;
    private double tot_sell_val_day;
    private double cost_price;
    private String instrument_type;
}
