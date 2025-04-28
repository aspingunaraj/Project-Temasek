package org.example.controller;

import org.example.config.MarketModeConfig;
import org.example.dataAnalysis.depthStrategy.StrategyOne;
import org.example.websocket.WebSocketService;
import org.example.websocket.model.StrategySummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Controller
public class ApiController {

    private final WebSocketService webSocketService;

    // ✅ Constructor injection
    @Autowired
    public ApiController(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }



    @PostMapping("/update-thresholds")
    @ResponseBody
    public String updateThresholds(@RequestBody Map<String, Double> thresholds) {

        return "Thresholds updated successfully!";
    }

    @GetMapping("/current-thresholds")
    @ResponseBody
    public Map<String, Double> getCurrentThresholds() {
        return null;
    }

    @GetMapping("/dashboard")
    public String dashboardPage() {
        return "dashboard";
    }

    @GetMapping("/strategy-outcome-dashboard")
    public String strategyOutcomeDashboard() {
        return "strategy-outcome-dashboard";
    }

    @GetMapping("/api/current-mode")
    @ResponseBody
    public String getCurrentMode() {
        return MarketModeConfig.isSimulationMode() ? "simulated" : "live";
    }

    private static final List<String> STRATEGIES = List.of(
            "orderBookPressure", "depthImbalance", "depthConvexity",
            "bidAskSpread", "top5Weight", "volumeAtPrice"
    );

    private static final String TRAINING_DATA_DIR = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/";

    @GetMapping("/api/strategy-summary")
    @ResponseBody
    public List<StrategySummary> getStrategySummary() {
        List<StrategySummary> result = new ArrayList<>();

        for (String strategy : STRATEGIES) {
            File file = new File(TRAINING_DATA_DIR + strategy + ".csv");
            if (!file.exists()) continue;

            int buySuccess = 0, buyFail = 0, sellSuccess = 0, sellFail = 0;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                boolean isHeader = true;
                while ((line = reader.readLine()) != null) {
                    if (isHeader) { isHeader = false; continue; }
                    String[] parts = line.split(",");
                    if (parts.length < 3) continue;
                    switch (parts[2].trim()) {
                        case "BUY_SUCCESS" -> buySuccess++;
                        case "BUY_FAILURE" -> buyFail++;
                        case "SELL_SUCCESS" -> sellSuccess++;
                        case "SELL_FAILURE" -> sellFail++;
                    }
                }

                // Add BUY row
                int buyTotal = buySuccess + buyFail;
                StrategySummary buy = new StrategySummary();
                buy.setStrategy(strategy);
                buy.setSignal("BUY");
                buy.setSuccess(buySuccess);
                buy.setFailure(buyFail);
                buy.setTotal(buyTotal);
                buy.setSuccessRate(buyTotal == 0 ? "0%" : String.format("%.2f%%", 100.0 * buySuccess / buyTotal));
                result.add(buy);

                // Add SELL row
                int sellTotal = sellSuccess + sellFail;
                StrategySummary sell = new StrategySummary();
                sell.setStrategy(strategy);
                sell.setSignal("SELL");
                sell.setSuccess(sellSuccess);
                sell.setFailure(sellFail);
                sell.setTotal(sellTotal);
                sell.setSuccessRate(sellTotal == 0 ? "0%" : String.format("%.2f%%", 100.0 * sellSuccess / sellTotal));
                result.add(sell);

            } catch (IOException e) {
                e.printStackTrace(); // or log
            }
        }

        return result;
    }

    @Controller
    public class StrategySummaryPageController {

        @GetMapping("/strategy-summary-table")
        public String viewSummaryPage(Model model) {
            return "strategyonesummary";  // Corresponds to strategy-summary.html
        }
    }

    @Controller
    public class TrainingPageController {

        @GetMapping("/trainingdata")
        public String viewSummaryPage(Model model) {
            return "trainingdata";  // Corresponds to strategy-summary.html
        }
    }


    @GetMapping("/api/training-data/{strategy}")
    @ResponseBody
    public List<Map<String, String>> getTrainingData(@PathVariable String strategy) {
        List<Map<String, String>> result = new ArrayList<>();
        File file = new File(TRAINING_DATA_DIR + strategy + ".csv");

        if (!file.exists()) return result;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) { isHeader = false; continue; }
                String[] parts = line.split(",");
                if (parts.length < 3) continue;

                Map<String, String> row = new LinkedHashMap<>();
                row.put("timestamp", parts[0]);
                row.put("feature", parts[1]);
                row.put("label", parts[2]);
                result.add(row);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    @GetMapping("/api/read-compressed-ticks")
    @ResponseBody
    public ResponseEntity<Resource> downloadCompressedFile() {
        Path path = Paths.get("src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/all_ticks.jsonl.gz");

        try {
            // Create resource from file
            Resource resource = new InputStreamResource(Files.newInputStream(path));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"all_ticks.jsonl.gz\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(path))
                    .body(resource);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @Controller
    public class PageRedirectController {

        @GetMapping("/read-compressed")
        public String showCompressedTickViewer() {
            return "read-compressed"; // resolves to src/main/resources/templates/read-compressed.html if using Thymeleaf
        }
    }




}
