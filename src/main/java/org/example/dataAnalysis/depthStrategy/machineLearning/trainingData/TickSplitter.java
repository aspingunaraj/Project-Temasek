package org.example.dataAnalysis.depthStrategy.machineLearning.trainingData;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class TickSplitter {

    private static final String TRAINING_INPUT_FILE = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/all_ticks.json";
    private static final String TESTING_INPUT_FILE = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/testingData/all_ticks.json";

    private static final String TRAINING_OUTPUT_DIR = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/";
    private static final String TESTING_OUTPUT_DIR = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/testingData/";

    private static final LocalDate TARGET_DATE = LocalDate.of(2025, 4, 30);
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");

    public static void main(String[] args) {
        //splitTickFileBySymbolId(TRAINING_INPUT_FILE, TRAINING_OUTPUT_DIR); // for training data
         splitTickFileBySymbolIdForTesting(TESTING_INPUT_FILE, TESTING_OUTPUT_DIR); // for testing data
        // mergeTickFiles(TRAINING_OUTPUT_DIR);
    }

    public static void splitTickFileBySymbolId(String inputFile, String outputDirectory) {
        performTickSplit(inputFile, outputDirectory);
    }

    public static void splitTickFileBySymbolIdForTesting(String inputFile, String outputDirectory) {
        performTickSplit(inputFile, outputDirectory);
    }

    private static void performTickSplit(String inputFile, String outputDirectory) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer();
        Map<Integer, BufferedWriter> writers = new HashMap<>();
        Set<Integer> initializedFiles = new HashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(inputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                JsonNode node = mapper.readTree(line);
                long lastTradedTimeEpoch = node.get("lastTradedTime").asLong();
                LocalDate tickDate = Instant.ofEpochSecond(lastTradedTimeEpoch).atZone(ZONE_ID).toLocalDate();

                if (tickDate.equals(TARGET_DATE)) {
                    continue; // Skip 2025-04-30 ticks
                }

                int securityId = node.get("securityId").asInt();
                String outputFileName = outputDirectory + "compressedTickDump_" + securityId + ".json";
                Path outputPath = Paths.get(outputFileName);

                if (!initializedFiles.contains(securityId)) {
                    Files.write(outputPath, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    initializedFiles.add(securityId);
                }

                BufferedWriter bw = writers.computeIfAbsent(securityId, id -> {
                    try {
                        return Files.newBufferedWriter(outputPath, StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to open file for writing: " + outputFileName, e);
                    }
                });

                bw.write(writer.writeValueAsString(node));
                bw.newLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            for (BufferedWriter bw : writers.values()) {
                try {
                    bw.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void mergeTickFiles(String outputDirectory) {
        List<String> filesToMerge = Arrays.asList(
                outputDirectory + "all_ticks2.json",
                outputDirectory + "all_ticks3.json",
                outputDirectory + "all_ticks4.json"
        );

        Path outputPath = Paths.get(outputDirectory + "all_ticks.json");

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String filePath : filesToMerge) {
                Path path = Paths.get(filePath);
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            writer.write(line);
                            writer.newLine();
                        }
                    }
                }
            }
            System.out.println("✅ Merged files into: " + outputPath.getFileName());
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("❌ Failed to merge files.");
        }
    }
}
