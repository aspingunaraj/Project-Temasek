package org.example.config;

public class MarketModeConfig {
    private static boolean simulationMode = true; // Set to false for live mode

    public static boolean isSimulationMode() {
        return simulationMode;
    }

    public static void setSimulationMode(boolean enabled) {
        simulationMode = enabled;
    }
}
