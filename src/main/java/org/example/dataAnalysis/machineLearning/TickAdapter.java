package org.example.dataAnalysis.machineLearning;

import org.example.websocket.model.DepthPacket;
import org.example.websocket.model.Tick;
import org.tribuo.Example;
import org.tribuo.Feature;
import org.tribuo.classification.Label;
import org.tribuo.impl.ListExample;
import org.tribuo.classification.LabelFactory;

import java.util.*;
import java.util.stream.Collectors;

public class TickAdapter {

    public static List<Tick> generateSimulatedTicks(int count) {
        List<Tick> ticks = new ArrayList<>();
        float price = 100f;
        Random rand = new Random();

        for (int i = 0; i < count; i++) {
            price += rand.nextFloat() * 3f - 1.5f;
            float bid = price - rand.nextFloat() * 0.5f;
            float ask = price + rand.nextFloat() * 0.5f;

            List<DepthPacket> depth = new ArrayList<>();
            for (int j = 0; j < 5; j++) {
                depth.add(new DepthPacket(
                        j,
                        rand.nextInt(500),
                        rand.nextInt(500),
                        (short) 0,
                        (short) 0,
                        bid,
                        ask
                ));
            }

            ticks.add(Tick.builder()
                    .lastTradedPrice(round(price))
                    .volumeTraded(rand.nextInt(9000) + 1000)
                    .totalBuyQuantity(rand.nextInt(5000))
                    .totalSellQuantity(rand.nextInt(5000))
                    .open(99f)
                    .close(101f)
                    .high(102f)
                    .low(98f)
                    .mbpRowPacket(depth)
                    .build());
        }

        return ticks;
    }

    public static List<LabeledTick> labelTicks(List<Tick> ticks, double threshold) {
        List<LabeledTick> labeled = new ArrayList<>();
        for (int i = 0; i < ticks.size() - 100; i++) {
            float current = ticks.get(i).getLastTradedPrice();
            float future = ticks.get(i + 100).getLastTradedPrice();
            String label;
            if (future > current + threshold) label = "BUY";
            else if (future < current - threshold) label = "SELL";
            else label = "HOLD";
            labeled.add(new LabeledTick(ticks.get(i), label));
        }
        return labeled;
    }

    public static List<Example<Label>> convertToExamplesWithHistory(List<LabeledTick> labeledTicks, List<Tick> history, LabelFactory factory) {
        List<Example<Label>> examples = new ArrayList<>();
        float epsilon = 1e-6f;

        for (int i = 0; i < labeledTicks.size(); i++) {
            LabeledTick labeledTick = labeledTicks.get(i);
            Tick currentTick = labeledTick.tick;

            int historyIndex = history.indexOf(currentTick);
            if (historyIndex < 50) continue; // skip if not enough history

            List<Tick> last10 = history.subList(historyIndex - 10, historyIndex);
            List<Tick> last20 = history.subList(historyIndex - 20, historyIndex);
            List<Tick> last50 = history.subList(historyIndex - 50, historyIndex);

            // Microstructure
            float bid = getBestBid(currentTick);
            float ask = getBestAsk(currentTick);
            float mid = (bid + ask) / 2f;
            float spreadPct = (ask - bid) / (mid + epsilon);
            float imbalance = (float) (currentTick.getTotalBuyQuantity() - currentTick.getTotalSellQuantity()) /
                    (currentTick.getTotalBuyQuantity() + currentTick.getTotalSellQuantity() + epsilon);
            float depthSkew = calculateDepthSkew(currentTick.getMbpRowPacket());

            // Momentum & Volatility
            float ltpReturn5 = pctChange(history.get(historyIndex - 5).getLastTradedPrice(), currentTick.getLastTradedPrice());
            float ltpReturn20 = pctChange(history.get(historyIndex - 20).getLastTradedPrice(), currentTick.getLastTradedPrice());
            float ltpStd20 = stdDev(last20.stream().map(Tick::getLastTradedPrice).collect(Collectors.toList()));
            float ltpStd50 = stdDev(last50.stream().map(Tick::getLastTradedPrice).collect(Collectors.toList()));

            float avgVol20 = (float) last20.stream().mapToLong(Tick::getVolumeTraded).average().orElse(0);
            float volumeRatio = currentTick.getVolumeTraded() / (float)(avgVol20 + epsilon);

            float avgLTP20 = (float) last20.stream().mapToDouble(Tick::getLastTradedPrice).average().orElse(0);
            float priceVsMA20 = (currentTick.getLastTradedPrice() - avgLTP20) / (avgLTP20 + epsilon);

            float avgSpread10 = (float) last10.stream().mapToDouble(t -> {
                float b = getBestBid(t);
                float a = getBestAsk(t);
                return (a - b) / ((a + b) / 2 + epsilon);
            }).average().orElse(0);

            float avgImbalance10 = (float) last10.stream().mapToDouble(t -> {
                return (t.getTotalBuyQuantity() - t.getTotalSellQuantity()) /
                        (double)(t.getTotalBuyQuantity() + t.getTotalSellQuantity() + epsilon);
            }).average().orElse(0);

            List<Feature> features = List.of(
                    new Feature("spread_pct", spreadPct),
                    new Feature("orderFlowImbalance", imbalance),
                    new Feature("depthSkew", depthSkew),
                    new Feature("ltp_return_5", ltpReturn5),
                    new Feature("ltp_return_20", ltpReturn20),
                    new Feature("ltp_std_20", ltpStd20),
                    new Feature("ltp_std_50", ltpStd50),
                    new Feature("volume_ratio", volumeRatio),
                    new Feature("price_vs_ma20", priceVsMA20),
                    new Feature("avg_spread_pct_10", avgSpread10),
                    new Feature("avg_imbalance_10", avgImbalance10)
            );

            examples.add(new ListExample<>(factory.generateOutput(labeledTick.label), features));
        }

        return examples;
    }

    private static float pctChange(float oldVal, float newVal) {
        return (newVal - oldVal) / (oldVal + 1e-6f);
    }

    private static float stdDev(List<Float> values) {
        double avg = values.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
        return (float) Math.sqrt(values.stream().mapToDouble(v -> (v - avg) * (v - avg)).average().orElse(0.0));
    }

    private static float getBestBid(Tick tick) {
        return tick.getMbpRowPacket() != null && !tick.getMbpRowPacket().isEmpty()
                ? tick.getMbpRowPacket().get(0).getBuyPrice()
                : tick.getLastTradedPrice();
    }

    private static float getBestAsk(Tick tick) {
        return tick.getMbpRowPacket() != null && !tick.getMbpRowPacket().isEmpty()
                ? tick.getMbpRowPacket().get(0).getSellPrice()
                : tick.getLastTradedPrice();
    }

    private static float calculateDepthSkew(List<DepthPacket> depth) {
        if (depth == null || depth.isEmpty()) return 0f;

        int buyTotal = 0, sellTotal = 0;
        for (DepthPacket packet : depth) {
            buyTotal += packet.getBuyQuantity();
            sellTotal += packet.getSellQuantity();
        }
        return (float) buyTotal / (buyTotal + sellTotal + 1e-6f);
    }

    private static float round(float val) {
        return Math.round(val * 100.0f) / 100.0f;
    }

    public static class LabeledTick {
        public Tick tick;
        public String label;

        public LabeledTick(Tick tick, String label) {
            this.tick = tick;
            this.label = label;
        }
    }

    public static Map<String, Double> extractFeatureMap(Tick currentTick, List<Tick> fullHistory) {
        int index = fullHistory.indexOf(currentTick);
        if (index < 50) return null; // Need enough history

        float epsilon = 1e-6f;

        List<Tick> last10 = fullHistory.subList(index - 10, index);
        List<Tick> last20 = fullHistory.subList(index - 20, index);
        List<Tick> last50 = fullHistory.subList(index - 50, index);

        float bid = getBestBid(currentTick);
        float ask = getBestAsk(currentTick);
        float mid = (bid + ask) / 2f;
        float spreadPct = (ask - bid) / (mid + epsilon);
        float imbalance = (float) (currentTick.getTotalBuyQuantity() - currentTick.getTotalSellQuantity()) /
                (currentTick.getTotalBuyQuantity() + currentTick.getTotalSellQuantity() + epsilon);
        float depthSkew = calculateDepthSkew(currentTick.getMbpRowPacket());

        float ltpReturn5 = pctChange(fullHistory.get(index - 5).getLastTradedPrice(), currentTick.getLastTradedPrice());
        float ltpReturn20 = pctChange(fullHistory.get(index - 20).getLastTradedPrice(), currentTick.getLastTradedPrice());
        float ltpStd20 = stdDev(last20.stream().map(Tick::getLastTradedPrice).collect(Collectors.toList()));
        float ltpStd50 = stdDev(last50.stream().map(Tick::getLastTradedPrice).collect(Collectors.toList()));

        float avgVol20 = (float) last20.stream().mapToLong(Tick::getVolumeTraded).average().orElse(0);
        float volumeRatio = currentTick.getVolumeTraded() / (float)(avgVol20 + epsilon);

        float avgLTP20 = (float) last20.stream().mapToDouble(Tick::getLastTradedPrice).average().orElse(0);
        float priceVsMA20 = (currentTick.getLastTradedPrice() - avgLTP20) / (avgLTP20 + epsilon);

        float avgSpread10 = (float) last10.stream().mapToDouble(t -> {
            float b = getBestBid(t);
            float a = getBestAsk(t);
            return (a - b) / ((a + b) / 2 + epsilon);
        }).average().orElse(0);

        float avgImbalance10 = (float) last10.stream().mapToDouble(t -> {
            return (t.getTotalBuyQuantity() - t.getTotalSellQuantity()) /
                    (double)(t.getTotalBuyQuantity() + t.getTotalSellQuantity() + epsilon);
        }).average().orElse(0);

        Map<String, Double> featureMap = new HashMap<>();
        featureMap.put("spread_pct", (double) spreadPct);
        featureMap.put("orderFlowImbalance", (double) imbalance);
        featureMap.put("depthSkew", (double) depthSkew);
        featureMap.put("ltp_return_5", (double) ltpReturn5);
        featureMap.put("ltp_return_20", (double) ltpReturn20);
        featureMap.put("ltp_std_20", (double) ltpStd20);
        featureMap.put("ltp_std_50", (double) ltpStd50);
        featureMap.put("volume_ratio", (double) volumeRatio);
        featureMap.put("price_vs_ma20", (double) priceVsMA20);
        featureMap.put("avg_spread_pct_10", (double) avgSpread10);
        featureMap.put("avg_imbalance_10", (double) avgImbalance10);
        return featureMap;

    }

}
