package org.example.websocket.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepthPacket {
    private int packetNo;
    private int buyQuantity;
    private int sellQuantity;
    private short buyOrder;
    private short sellOrder;
    private float buyPrice;
    private float sellPrice;
}
