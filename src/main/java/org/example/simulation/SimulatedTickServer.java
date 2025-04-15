package org.example.simulation;

import org.example.websocket.model.DepthPacket;
import org.example.websocket.model.Tick;

import java.util.*;
import java.util.function.Consumer;

public class SimulatedTickServer {

    private final List<Integer> symbolIds = Arrays.asList(3787);
    private final Consumer<Tick> consumer;
    private final Random random = new Random();

    public SimulatedTickServer(Consumer<Tick> consumer) {
        this.consumer = consumer;
    }

    public void startStreaming() {
        new Thread(() -> {
            int index = 0;
            while (true) {
                int symbolId = symbolIds.get(index % symbolIds.size());
                Tick tick = generateTick(symbolId);
                consumer.accept(tick);
                index++;

                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private Tick generateTick(int symbolId) {
        float ltp = 100 + random.nextFloat() * 20;
        float step = 0.1f;

        List<DepthPacket> depth = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            depth.add(new DepthPacket(i, random.nextInt(200), random.nextInt(200), (short) 1, (short) 1,
                    ltp - (i + 1) * step, ltp + (i + 1) * step));
        }

        return Tick.builder()
                .securityId(symbolId)
                .lastTradedPrice(ltp)
                .lastUpdatedTime(System.currentTimeMillis())
                .lastTradedTime(System.currentTimeMillis())
                .lastTradedQuantity(random.nextInt(50))
                .volumeTraded(random.nextInt(10000))
                .mbpRowPacket(depth)
                .build();
    }
}
