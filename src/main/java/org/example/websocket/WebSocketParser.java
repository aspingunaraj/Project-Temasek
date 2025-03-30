package org.example.websocket;

import org.example.websocket.model.DepthPacket;
import org.example.websocket.model.Tick;
import org.example.websocket.util.EpochConverterUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.util.ArrayList;

import static org.example.websocket.constant.ByteConversionConstants.*;
import static org.example.websocket.constant.ByteResponseCreationConstants.*;

public class WebSocketParser {

    private static final DecimalFormat decimalFormat = new DecimalFormat("0.00");

    public ArrayList<Tick> parse(ByteBuffer bufferPackets) {
        ArrayList<Tick> ticks = new ArrayList<>();
        ByteBuffer packet = ByteBuffer.allocate(bufferPackets.capacity()).order(ByteOrder.LITTLE_ENDIAN);
        bufferPackets.position(0);
        packet.put(bufferPackets);
        int position = 0, bufferLength = packet.capacity();

        while (position < bufferLength) {
            byte packetType = packet.get(position);
            switch (packetType) {
                case INDEX_LTP_PKT:
                    ticks.add(parseIndexLtp(packet, position));
                    position += INDEX_LTP_PACKET_SIZE;
                    break;
                case INDEX_QUOTE_PKT:
                    ticks.add(parseIndexQuote(packet, position));
                    position += INDEX_QUOTE_PACKET_SIZE;
                    break;
                case INDEX_FULL_PKT:
                    ticks.add(parseIndexFull(packet, position));
                    position += INDEX_FULL_PACKET_SIZE;
                    break;
                case LTP_PKT:
                    ticks.add(parseLtp(packet, position));
                    position += LTP_PACKET_SIZE;
                    break;
                case QUOTE_PKT:
                    ticks.add(parseQuote(packet, position));
                    position += QUOTE_PACKET_SIZE;
                    break;
                case FULL_PKT:
                    ticks.add(parseFull(packet, position));
                    position += FULL_PACKET_SIZE;
                    break;
            }
        }
        return ticks;
    }

    private Tick parseIndexLtp(ByteBuffer packet, int pos) {
        return Tick.builder()
                .lastTradedPrice(packet.getFloat(pos + LTP_OFFSET))
                .lastUpdatedTime(EpochConverterUtil.epochConverter(packet.getInt(pos + INDEX_LTP_LUT_OFFSET)))
                .securityId(packet.getInt(pos + INDEX_LTP_SECURITY_ID_OFFSET))
                .tradable(packet.get(pos + INDEX_LTP_TRADABLE_OFFSET))
                .mode(packet.get(pos + INDEX_LTP_MODETYPE_OFFSET))
                .changeAbsolute(Float.parseFloat(decimalFormat.format(packet.getFloat(pos + INDEX_LTP_CHANGE_ABSOLUTE_OFFSET))))
                .changePercent(packet.getFloat(pos + INDEX_LTP_CHANGE_PERCENT_OFFSET))
                .build();
    }

    private Tick parseIndexQuote(ByteBuffer packet, int pos) {
        return Tick.builder()
                .lastTradedPrice(packet.getFloat(pos + LTP_OFFSET))
                .securityId(packet.getInt(pos + INDEX_QUOTE_SECURITY_ID_OFFSET))
                .tradable(packet.get(pos + INDEX_QUOTE_TRADABLE_OFFSET))
                .mode(packet.get(pos + INDEX_QUOTE_MODETYPE_OFFSET))
                .open(packet.getFloat(pos + INDEX_QUOTE_OPEN_OFFSET))
                .close(packet.getFloat(pos + INDEX_QUOTE_CLOSE_OFFSET))
                .high(packet.getFloat(pos + INDEX_QUOTE_HIGH_OFFSET))
                .low(packet.getFloat(pos + INDEX_QUOTE_LOW_OFFSET))
                .changePercent(packet.getFloat(pos + INDEX_QUOTE_CHANGE_PERCENT_OFFSET))
                .changeAbsolute(Float.parseFloat(decimalFormat.format(packet.getFloat(pos + INDEX_QUOTE_CHANGE_ABSOLUTE_OFFSET))))
                .fiftyTwoWeekHigh(packet.getFloat(pos + INDEX_QUOTE_52_WEEK_HIGH_OFFSET))
                .fiftyTwoWeekLow(packet.getFloat(pos + INDEX_QUOTE_52_WEEK_LOW_OFFSET))
                .build();
    }

    private Tick parseIndexFull(ByteBuffer packet, int pos) {
        return Tick.builder()
                .lastTradedPrice(packet.getFloat(pos + LTP_OFFSET))
                .securityId(packet.getInt(pos + INDEX_FULL_SECURITY_ID_OFFSET))
                .tradable(packet.get(pos + INDEX_FULL_TRADABLE_OFFSET))
                .mode(packet.get(pos + INDEX_FULL_MODETYPE_OFFSET))
                .open(packet.getFloat(pos + INDEX_FULL_OPEN_OFFSET))
                .close(packet.getFloat(pos + INDEX_FULL_CLOSE_OFFSET))
                .high(packet.getFloat(pos + INDEX_FULL_HIGH_OFFSET))
                .low(packet.getFloat(pos + INDEX_FULL_LOW_OFFSET))
                .changePercent(packet.getFloat(pos + INDEX_FULL_CHANGE_PERCENT_OFFSET))
                .changeAbsolute(Float.parseFloat(decimalFormat.format(packet.getFloat(pos + INDEX_FULL_CHANGE_ABSOLUTE_OFFSET))))
                .lastUpdatedTime(EpochConverterUtil.epochConverter(packet.getInt(pos + INDEX_FULL_LUT_OFFSET)))
                .build();
    }

    private Tick parseLtp(ByteBuffer packet, int pos) {
        return Tick.builder()
                .lastTradedPrice(packet.getFloat(pos + LTP_OFFSET))
                .lastTradedTime(EpochConverterUtil.epochConverter(packet.getInt(pos + LTP_LTT_OFFSET)))
                .securityId(packet.getInt(pos + LTP_SECURITY_ID_OFFSET))
                .tradable(packet.get(pos + LTP_TRADABLE_OFFSET))
                .mode(packet.get(pos + LTP_MODETYPE_OFFSET))
                .changeAbsolute(Float.parseFloat(decimalFormat.format(packet.getFloat(pos + LTP_CHANGE_ABSOLUTE_OFFSET))))
                .changePercent(packet.getFloat(pos + LTP_CHANGE_PERCENT_OFFSET))
                .build();
    }

    private Tick parseQuote(ByteBuffer packet, int pos) {
        return Tick.builder()
                .lastTradedPrice(packet.getFloat(pos + LTP_OFFSET))
                .lastTradedTime(EpochConverterUtil.epochConverter(packet.getInt(pos + QUOTE_LTT_OFFSET)))
                .securityId(packet.getInt(pos + QUOTE_SECURITY_ID_OFFSET))
                .tradable(packet.get(pos + QUOTE_TRADABLE_OFFSET))
                .mode(packet.get(pos + QUOTE_MODETYPE_OFFSET))
                .lastTradedQuantity(packet.getInt(pos + QUOTE_LAST_TRADED_QUANTITY_OFFSET))
                .averageTradedPrice(packet.getFloat(pos + QUOTE_AVG_TRADED_PRICE_OFFSET))
                .volumeTraded(packet.getInt(pos + QUOTE_VOLUME_OFFSET))
                .totalBuyQuantity(packet.getInt(pos + QUOTE_TOTAL_BUY_QUANTITY_OFFSET))
                .totalSellQuantity(packet.getInt(pos + QUOTE_TOTAL_SELL_QUANTITY_OFFSET))
                .open(packet.getFloat(pos + QUOTE_OPEN_OFFSET))
                .close(packet.getFloat(pos + QUOTE_CLOSE_OFFSET))
                .high(packet.getFloat(pos + QUOTE_HIGH_OFFSET))
                .low(packet.getFloat(pos + QUOTE_LOW_OFFSET))
                .changePercent(packet.getFloat(pos + QUOTE_CHANGE_PERCENT_OFFSET))
                .changeAbsolute(Float.parseFloat(decimalFormat.format(packet.getFloat(pos + QUOTE_CHANGE_ABSOLUTE_OFFSET))))
                .fiftyTwoWeekHigh(packet.getFloat(pos + QUOTE_52_WEEK_HIGH_OFFSET))
                .fiftyTwoWeekLow(packet.getFloat(pos + QUOTE_52_WEEK_LOW_OFFSET))
                .build();
    }

    private Tick parseFull(ByteBuffer packet, int pos) {
        ArrayList<DepthPacket> depthPacketList = new ArrayList<>();
        for (int i = 0; i < DEPTH_PACKETS_COUNT; i++) {
            depthPacketList.add(DepthPacket.builder()
                    .packetNo(i + 1)
                    .buyQuantity(packet.getInt(pos + DEPTH_BUY_QUANTITY_OFFSET + (i * DEPTH_PACKET_SIZE)))
                    .sellQuantity(packet.getInt(pos + DEPTH_SELL_QUANTITY_OFFSET + (i * DEPTH_PACKET_SIZE)))
                    .buyOrder(packet.getShort(pos + DEPTH_BUY_ORDER_OFFSET + (i * DEPTH_PACKET_SIZE)))
                    .sellOrder(packet.getShort(pos + DEPTH_SELL_ORDER_OFFSET + (i * DEPTH_PACKET_SIZE)))
                    .buyPrice(packet.getFloat(pos + DEPTH_BUY_PRICE_OFFSET + (i * DEPTH_PACKET_SIZE)))
                    .sellPrice(packet.getFloat(pos + DEPTH_SELL_PRICE_OFFSET + (i * DEPTH_PACKET_SIZE)))
                    .build());
        }

        return Tick.builder()
                .mbpRowPacket(depthPacketList)
                .lastTradedPrice(packet.getFloat(pos + FULL_LTP_OFFSET))
                .lastTradedTime(EpochConverterUtil.epochConverter(packet.getInt(pos + FULL_LTT_OFFSET)))
                .securityId(packet.getInt(pos + FULL_SECURITY_ID_OFFSET))
                .tradable(packet.get(pos + FULL_TRADABLE_OFFSET))
                .mode(packet.get(pos + FULL_MODETYPE_OFFSET))
                .lastTradedQuantity(packet.getInt(pos + FULL_LAST_TRADED_QUANTITY_OFFSET))
                .averageTradedPrice(packet.getFloat(pos + FULL_AVG_TRADED_PRICE_OFFSET))
                .volumeTraded(packet.getInt(pos + FULL_VOLUME_OFFSET))
                .totalBuyQuantity(packet.getInt(pos + FULL_TOTAL_BUY_QUANTITY_OFFSET))
                .totalSellQuantity(packet.getInt(pos + FULL_TOTAL_SELL_QUANTITY_OFFSET))
                .open(packet.getFloat(pos + FULL_OPEN_OFFSET))
                .close(packet.getFloat(pos + FULL_CLOSE_OFFSET))
                .high(packet.getFloat(pos + FULL_HIGH_OFFSET))
                .low(packet.getFloat(pos + FULL_LOW_OFFSET))
                .changePercent(packet.getFloat(pos + FULL_CHANGE_PERCENT_OFFSET))
                .changeAbsolute(Float.parseFloat(decimalFormat.format(packet.getFloat(pos + FULL_CHANGE_ABSOLUTE_OFFSET))))
                .fiftyTwoWeekHigh(packet.getFloat(pos + FULL_52_WEEK_HIGH_OFFSET))
                .fiftyTwoWeekLow(packet.getFloat(pos + FULL_52_WEEK_LOW_OFFSET))
                .oi(packet.getInt(pos + FULL_OI_OFFSET))
                .oiChange(packet.getInt(pos + FULL_CHANGE_OI_OFFSET))
                .build();
    }
}
