package org.example.websocket;

import org.example.Main;
import org.example.config.MarketModeConfig;
import org.example.dataAnalysis.StrategyManager;
import org.example.dataAnalysis.depthStrategy.StrategyOne;
import org.example.simulation.SimulatedTickServer;
import org.example.tradeGovernance.OrderServices;
import org.example.tradeGovernance.TradeAnalysis;
import org.example.websocket.dataPreparation.DepthPacketHistoryManager;
import org.example.websocket.dataPreparation.SubscriptionPreferenceBuilder;
import org.example.websocket.model.PreferenceDto;
import org.example.websocket.model.Tick;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebSocketService {

    private final Map<Integer, Long> lastEvaluated = new ConcurrentHashMap<>();
    private static final long EVALUATION_COOLDOWN_MS = 2000;

    // 🔁 Track active connections
    private volatile Thread simulatedThread = null;
    private volatile WebSocketClient liveClient = null;

    public synchronized void startWebSocket() {
        stopWebSocket(); // 🔁 Stop any existing connection first

        if (MarketModeConfig.isSimulationMode()) {
            System.out.println("🧪 Starting in Simulation Mode");

            SimulatedTickServer streamer = new SimulatedTickServer(tick ->
                    processTick(tick, DepthPacketHistoryManager.getInstance()));

            simulatedThread = new Thread(streamer::startStreaming);
            simulatedThread.start();

        } else {
            System.out.println("🌐 Starting in Live Market Mode");
            startLiveMarketWebSocket();
        }
    }

    public synchronized void stopWebSocket() {
        try {
            if (liveClient != null) {
                liveClient.closeConnection();
                liveClient = null;
                System.out.println("🔌 Live market WebSocket stopped.");
            }

            if (simulatedThread != null && simulatedThread.isAlive()) {
                simulatedThread.interrupt();
                simulatedThread = null;
                System.out.println("🧪 Simulated WebSocket stopped.");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error while stopping WebSocket: " + e.getMessage());
        }
    }

    private void startLiveMarketWebSocket() {
        try {
            liveClient = new WebSocketClient(Main.publicAccessToken);

            liveClient.setOnOpenListener(() -> System.out.println("📡 WebSocket connected."));
            liveClient.setOnCloseListener(reason -> System.out.println("🔌 WebSocket closed: " + reason));

            liveClient.setOnErrorListener(new org.example.websocket.listeners.OnErrorListener() {
                @Override public void onError(String errorMessage) {
                    System.err.println("❌ WebSocket Error: " + errorMessage);
                }

                @Override public void onError(Exception e) {
                    e.printStackTrace();
                }

                @Override public void onError(RuntimeException re) {
                    re.printStackTrace();
                }
            });

            DepthPacketHistoryManager historyManager = DepthPacketHistoryManager.getInstance();

            liveClient.setOnMessageListener(ticks -> {
                for (Tick tick : ticks) {
                    processTick(tick, historyManager);
                }
            });

            liveClient.connect();
            Thread.sleep(1000);
            List<PreferenceDto> prefs = SubscriptionPreferenceBuilder.buildPreferences();
            liveClient.subscribe(prefs);

        } catch (Exception e) {
            System.err.println("❌ WebSocket error during startup: " + e.getMessage());
        }
    }

    private void processTick(Tick tick, DepthPacketHistoryManager historyManager) {
        int symbolId = tick.getSecurityId();

        if (tick.getMbpRowPacket() != null && !tick.getMbpRowPacket().isEmpty()) {
            historyManager.addTick(tick, () -> checkAndTrainModelIfReady(symbolId));
        }

        long now = System.currentTimeMillis();
        if (lastEvaluated.containsKey(symbolId) &&
                now - lastEvaluated.get(symbolId) < EVALUATION_COOLDOWN_MS) {
            return;
        }

        lastEvaluated.put(symbolId, now);

        List<Tick> recentTicks = historyManager.getTickHistory(symbolId);
        StrategyOne.Signal signal = StrategyManager.strategySelector(recentTicks, symbolId);

        TradeAnalysis.Action action = new TradeAnalysis().evaluateTradeAction(symbolId, signal, Main.accessToken);

        if (action == TradeAnalysis.Action.BUY || action == TradeAnalysis.Action.SELL) {
            new OrderServices().orderManagement(String.valueOf(symbolId), action, tick.getLastTradedPrice());
        }
    }

    private void checkAndTrainModelIfReady(int symbolId) {
        List<Tick> history = DepthPacketHistoryManager.getInstance().getTickHistory(symbolId);
        if (history.size() < 300) return;


    }
}
