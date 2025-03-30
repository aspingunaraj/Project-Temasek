package org.example.websocket.dataPreparation;

import org.example.websocket.model.Tick;

import java.util.*;

public class DepthPacketHistoryManager {

    // 🔁 Singleton instance
    private static final DepthPacketHistoryManager INSTANCE = new DepthPacketHistoryManager();

    // 🗃️ Map to hold securityId and their rolling full Tick history (max 10 per security)
    private final Map<Integer, LinkedList<Tick>> tickHistory = new HashMap<>();

    // 🔒 Private constructor
    private DepthPacketHistoryManager() {}

    // 🔓 Public accessor
    public static DepthPacketHistoryManager getInstance() {
        return INSTANCE;
    }

    /**
     * Adds a new Tick to the rolling history (max 10 entries per security).
     */
    public synchronized void addTick(Tick tick) {
        int securityId = tick.getSecurityId();

        if (tick == null) return;

        tickHistory.putIfAbsent(securityId, new LinkedList<>());
        LinkedList<Tick> historyList = tickHistory.get(securityId);

        if (historyList.size() == 10) {
            historyList.removeFirst(); // remove oldest
        }

        historyList.addLast(tick); // save full Tick
    }

    /**
     * Get the rolling Tick history for a security ID.
     */
    public synchronized List<Tick> getTickHistory(int securityId) {
        return tickHistory.getOrDefault(securityId, new LinkedList<>());
    }

    /**
     * Clear all Tick histories
     */
    public synchronized void clearAll() {
        tickHistory.clear();
    }

    /**
     * Clear history for a specific securityId
     */
    public synchronized void clearHistory(int securityId) {
        tickHistory.remove(securityId);
    }
}
