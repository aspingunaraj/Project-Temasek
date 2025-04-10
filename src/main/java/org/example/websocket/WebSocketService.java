package org.example.websocket;

import org.example.Main;
import org.example.dataAnalysis.StrategyManager;
import org.example.dataAnalysis.depthStrategy.SignalOutcomeTracker;
import org.example.dataAnalysis.depthStrategy.StrategyOne;
import org.example.dataAnalysis.machineLearning.TickAdapter;
import org.example.dataAnalysis.machineLearning.TrainingDataWriter;
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

    public void startWebSocket() {
        try {
            WebSocketClient webSocketClient = new WebSocketClient(Main.publicAccessToken);

            webSocketClient.setOnOpenListener(() -> System.out.println("📡 WebSocket connected."));
            webSocketClient.setOnCloseListener(reason -> System.out.println("🔌 WebSocket closed: " + reason));

            webSocketClient.setOnErrorListener(new org.example.websocket.listeners.OnErrorListener() {
                public void onError(String errorMessage) { System.err.println("WebSocket Error: " + errorMessage); }
                public void onError(Exception e) { e.printStackTrace(); }
                public void onError(RuntimeException re) { re.printStackTrace(); }
            });
            DepthPacketHistoryManager historyManager = DepthPacketHistoryManager.getInstance();

            webSocketClient.setOnMessageListener(ticks -> {
                for (Tick tick : ticks) {
                    int symbolId = tick.getSecurityId();
                    SignalOutcomeTracker.evaluate(symbolId, tick.getLastTradedPrice());

                    if (tick.getMbpRowPacket() != null && !tick.getMbpRowPacket().isEmpty()) {
                        historyManager.addTick(tick, () -> checkAndTrainModelIfReady(symbolId));
                    }

                    long now = System.currentTimeMillis();
                    if (lastEvaluated.containsKey(symbolId) &&
                            now - lastEvaluated.get(symbolId) < EVALUATION_COOLDOWN_MS) {
                        continue;
                    }

                    lastEvaluated.put(symbolId, now);
                    StrategyOne.Signal signal = StrategyManager.strategySelector(historyManager.getTickHistory(symbolId), symbolId);
                    if (signal == StrategyOne.Signal.BUY || signal == StrategyOne.Signal.SELL) {
                        SignalOutcomeTracker.registerSignal(symbolId, tick.getLastTradedPrice(), signal);
                    }

                    TradeAnalysis.Action action = new TradeAnalysis().evaluateTradeAction(symbolId, signal, Main.accessToken);
                    if (action == TradeAnalysis.Action.BUY || action == TradeAnalysis.Action.SELL) {
                        new OrderServices().orderManagement(String.valueOf(symbolId), action, tick.getLastTradedPrice());
                    }
                }
            });

            webSocketClient.connect();
            Thread.sleep(1000);
            List<PreferenceDto> prefs = SubscriptionPreferenceBuilder.buildPreferences();
            webSocketClient.subscribe(prefs);
        } catch (Exception e) {
            System.err.println("❌ WebSocket error: " + e.getMessage());
        }
    }

    private void checkAndTrainModelIfReady(int symbolId) {
        List<Tick> history = DepthPacketHistoryManager.getInstance().getTickHistory(symbolId);
        if (history.size() < 300) return;

        try {
            List<TickAdapter.LabeledTick> labeledTicks = TickAdapter.labelTicks(history, 0.3);
            for (TickAdapter.LabeledTick labeled : labeledTicks) {
                Map<String, Double> features = TickAdapter.extractFeatureMap(labeled.tick, history);
                if (features != null) {
                    TrainingDataWriter.appendTrainingExample(new TrainingDataWriter.TrainingExample(features, labeled.label));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Training data generation failed: " + e.getMessage());
        }
    }
}
