package org.example.dataAnalysis.depthStrategy.machineLearning.backTesting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.websocket.model.DepthPacket;
import org.example.websocket.model.Tick;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class BackTesterUtility {

    public static List<Tick> loadTicks(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<Tick> ticks = new ArrayList<>();
        Files.lines(Paths.get(filePath)).forEach(line -> {
            try {
                ticks.add(mapper.readValue(line, Tick.class));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return ticks;
    }

    public static Tick aggregateTicks(List<Tick> ticks) {
        return Tick.builder()
                .lastTradedTime(ticks.get(ticks.size() - 1).getLastTradedTime())
                .securityId(ticks.get(0).getSecurityId())
                .lastTradedPrice(ticks.get(ticks.size() - 1).getLastTradedPrice())
                .lastTradedQuantity(ticks.stream().mapToInt(Tick::getLastTradedQuantity).sum())
                .averageTradedPrice((float) ticks.stream().mapToDouble(t -> t.getAverageTradedPrice() * t.getLastTradedQuantity()).sum()
                        / ticks.stream().mapToInt(Tick::getLastTradedQuantity).sum())
                .volumeTraded(ticks.stream().mapToLong(Tick::getVolumeTraded).max().orElse(0))
                .totalBuyQuantity(ticks.stream().mapToInt(Tick::getTotalBuyQuantity).max().orElse(0))
                .totalSellQuantity(ticks.stream().mapToInt(Tick::getTotalSellQuantity).max().orElse(0))
                .open(ticks.get(0).getOpen())
                .close(ticks.get(ticks.size() - 1).getClose())
                .high((float) ticks.stream().mapToDouble(Tick::getHigh).max().orElse(0))
                .low((float) ticks.stream().mapToDouble(Tick::getLow).min().orElse(Float.MAX_VALUE))
                .changePercent(ticks.get(ticks.size() - 1).getChangePercent())
                .changeAbsolute(ticks.get(ticks.size() - 1).getChangeAbsolute())
                .fiftyTwoWeekHigh(ticks.get(ticks.size() - 1).getFiftyTwoWeekHigh())
                .fiftyTwoWeekLow(ticks.get(ticks.size() - 1).getFiftyTwoWeekLow())
                .oi(ticks.get(ticks.size() - 1).getOi())
                .oiChange(ticks.get(ticks.size() - 1).getOiChange())
                .mbpRowPacket(aggregateDepthPackets(ticks))
                .build();
    }

    private static List<DepthPacket> aggregateDepthPackets(List<Tick> ticks) {
        Map<Integer, List<DepthPacket>> groupedByPacketNo = new HashMap<>();

        for (Tick tick : ticks) {
            for (DepthPacket dp : tick.getMbpRowPacket()) {
                groupedByPacketNo.computeIfAbsent(dp.getPacketNo(), k -> new ArrayList<>()).add(dp);
            }
        }

        return groupedByPacketNo.entrySet().stream().map(entry -> {
            List<DepthPacket> packets = entry.getValue();
            return DepthPacket.builder()
                    .packetNo(entry.getKey())
                    .buyQuantity(packets.stream().mapToInt(DepthPacket::getBuyQuantity).sum())
                    .sellQuantity(packets.stream().mapToInt(DepthPacket::getSellQuantity).sum())
                    .buyOrder((short) packets.stream().mapToInt(DepthPacket::getBuyOrder).max().orElse(0))
                    .sellOrder((short) packets.stream().mapToInt(DepthPacket::getSellOrder).max().orElse(0))
                    .buyPrice((float) packets.stream().mapToDouble(DepthPacket::getBuyPrice).average().orElse(0))
                    .sellPrice((float) packets.stream().mapToDouble(DepthPacket::getSellPrice).average().orElse(0))
                    .build();
        }).sorted(Comparator.comparingInt(DepthPacket::getPacketNo)).collect(Collectors.toList());
    }

    public static double calculateOBImbalance(Tick tick) {
        double bidVolume = 0, askVolume = 0;
        for (DepthPacket dp : tick.getMbpRowPacket()) {
            bidVolume += dp.getBuyQuantity();
            askVolume += dp.getSellQuantity();
        }
        return (bidVolume - askVolume) / (bidVolume + askVolume + 1e-6); // to avoid divide by zero
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String formatTime(long epochSeconds) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.of("Asia/Kolkata")).format(FORMATTER);
    }
    public static void printTrades(List<Trade> trades) {

        System.out.printf("%-15s %-15s %-10s %-12s %-12s %-18s %-25s %-10s%n",
                "Entry Time", "Exit Time", "Type", "Entry Price", "Exit Price", "OB Imbalance @ Entry", "Exit Reason", "P&L");

        double totalPnL = 0;
        int wins = 0, losses = 0;

        for (Trade trade : trades) {
            totalPnL += trade.pnl;
            if (trade.pnl > 0) wins++;
            else if (trade.pnl < 0) losses++;

            System.out.printf("%-20s %-20s %-10s %-12.2f %-12.2f %-18.2f %-25s %-10.4f%n",
                    formatTime(trade.entryTime), formatTime(trade.exitTime), trade.entryType,
                    trade.entryPrice, trade.exitPrice, trade.entryImbalance, trade.exitReason, trade.pnl);
        }

        System.out.println("---------------------------------------------------------------------------------------------");
        System.out.printf("Total Trades: %d | Wins: %d | Losses: %d | Net P&L: %.2f%n",
                trades.size(), wins, losses, totalPnL);
    }



}
