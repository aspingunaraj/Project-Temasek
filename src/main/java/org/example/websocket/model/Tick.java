package org.example.websocket.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tick {
    private float lastTradedPrice;
    private long lastTradedTime;
    private long lastUpdatedTime;
    private int securityId;
    private byte tradable;
    private byte mode;
    private int lastTradedQuantity;
    private float averageTradedPrice;
    private long volumeTraded;
    private int totalBuyQuantity;
    private int totalSellQuantity;
    private float open;
    private float close;
    private float high;
    private float low;
    private float changePercent;
    private float changeAbsolute;
    private float fiftyTwoWeekHigh;
    private float fiftyTwoWeekLow;
    private long oi;
    private long oiChange;
    private List<DepthPacket> mbpRowPacket;
}

