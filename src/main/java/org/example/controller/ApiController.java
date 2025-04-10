package org.example.controller;

import org.example.dataAnalysis.depthStrategy.StrategyOne;
import org.example.dataAnalysis.depthStrategy.SignalOutcomeTracker;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Controller
public class ApiController {

    @GetMapping({"/strategy-stats", "/strategy-stats.html"})
    public String showStrategyStats(Model model) {
        model.addAttribute("strategyStats", StrategyOne.getStrategyStats());
        return "strategy-stats";
    }

    @PostMapping("/update-thresholds")
    @ResponseBody
    public String updateThresholds(@RequestBody Map<String, Double> thresholds) {
        StrategyOne.updateThresholds(thresholds);
        return "Thresholds updated successfully!";
    }

    @GetMapping("/current-thresholds")
    @ResponseBody
    public Map<String, Double> getCurrentThresholds() {
        return StrategyOne.getThresholds();
    }

    @GetMapping({"/dashboard"})
    public String dashboardPage() {
        return "dashboard";
    }

    @GetMapping({"/signal-outcomes", "/signal-outcomes.html"})
    public String signalOutcomesPage() {
        return "signal-outcomes";
    }

    @GetMapping("/api/signal-outcomes")
    @ResponseBody
    public List<SignalOutcomeTracker.SignalOutcome> getSignalOutcomes() {
        return SignalOutcomeTracker.getCompletedSignals();
    }
}
