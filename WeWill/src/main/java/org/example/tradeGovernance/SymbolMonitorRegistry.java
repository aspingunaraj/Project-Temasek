package org.example.tradeGovernance;

import java.util.concurrent.ConcurrentHashMap;

public class SymbolMonitorRegistry {
    private static final ConcurrentHashMap<String, Boolean> activeMonitors = new ConcurrentHashMap<>();

    public static boolean isMonitoring(String securityId) {
        return activeMonitors.getOrDefault(securityId, false);
    }

    public static synchronized boolean startMonitoring(String securityId) {
        if (isMonitoring(securityId)) return false;
        activeMonitors.put(securityId, true);
        return true;
    }

    public static void stopMonitoring(String securityId) {
        activeMonitors.remove(securityId);
    }
}

