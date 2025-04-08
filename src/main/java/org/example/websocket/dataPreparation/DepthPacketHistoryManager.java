package org.example.websocket.dataPreparation;

import org.example.websocket.model.Tick;

import java.util.*;

public class DepthPacketHistoryManager {

    private static final DepthPacketHistoryManager INSTANCE = new DepthPacketHistoryManager();

    // Main 500-tick rolling evaluation buffer per security ID
    private final Map<Integer, LinkedList<Tick>> tickHistory = new HashMap<>();

    // Dedicated training buffer (flushes after 300 ticks)
    private final Map<Integer, LinkedList<Tick>> trainingTickBuffer = new HashMap<>();

    private DepthPacketHistoryManager() {}

    public static DepthPacketHistoryManager getInstance() {
        return INSTANCE;
    }

    /**
     * Adds a new Tick to both evaluation and training buffers.
     * Automatically calls onReadyToTrain if 300 ticks collected for training.
     */
    public synchronized void addTick(Tick tick, Runnable onReadyToTrain) {
        int securityId = tick.getSecurityId();
        if (tick == null) return;

        // ✅ Add to evaluation history (rolling 500)
        tickHistory.putIfAbsent(securityId, new LinkedList<>());
        LinkedList<Tick> evalHistory = tickHistory.get(securityId);
        if (evalHistory.size() == 500) evalHistory.removeFirst();
        evalHistory.addLast(tick);

        // ✅ Add to training buffer
        trainingTickBuffer.putIfAbsent(securityId, new LinkedList<>());
        LinkedList<Tick> trainingHistory = trainingTickBuffer.get(securityId);
        trainingHistory.addLast(tick);

        // ✅ Trigger training if buffer is full
        if (trainingHistory.size() >= 300) {
            onReadyToTrain.run();
            trainingTickBuffer.remove(securityId);
        }
    }

    /**
     * Get the 500-tick rolling evaluation buffer.
     */
    public synchronized List<Tick> getTickHistory(int securityId) {
        return tickHistory.getOrDefault(securityId, new LinkedList<>());
    }

    /**
     * (Optional) Get the training buffer for a security.
     */
    public synchronized List<Tick> getTrainingHistory(int securityId) {
        return trainingTickBuffer.getOrDefault(securityId, new LinkedList<>());
    }

    public synchronized void clearAll() {
        tickHistory.clear();
        trainingTickBuffer.clear();
    }

    public synchronized void clearHistory(int securityId) {
        tickHistory.remove(securityId);
        trainingTickBuffer.remove(securityId);
    }
}
