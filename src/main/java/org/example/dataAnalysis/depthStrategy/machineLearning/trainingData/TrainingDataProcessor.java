package org.example.dataAnalysis.depthStrategy.machineLearning.trainingData;

import org.example.dataAnalysis.depthStrategy.machineLearning.backTesting.BackTesterMLDriven2;

import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;

public class TrainingDataProcessor {

    private static final String COMPRESSED_FILE = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/marketdata.jsonl.gz";
    private static final String OUTPUT_FILE = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/all_ticks.json";
    private static final String TRAINING_INPUT_FILE = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/all_ticks.json";
    private static final String TRAINING_OUTPUT_DIR = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/";

    public static void clearMarketDataFile() {
        Path path = Paths.get(COMPRESSED_FILE);
        try {
            Files.newBufferedWriter(path, StandardOpenOption.TRUNCATE_EXISTING).close();
            System.out.println("✅ Cleared file contents: " + COMPRESSED_FILE);
        } catch (IOException e) {
            System.err.println("❌ Failed to clear file contents: " + e.getMessage());
        }
    }

    public static void convertToAllTicksJson() {
        Path inputPath = Paths.get(COMPRESSED_FILE);
        Path outputPath = Paths.get(OUTPUT_FILE);

        try (GZIPInputStream gzipIn = new GZIPInputStream(Files.newInputStream(inputPath));
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzipIn));
             BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }

            System.out.println("✅ Converted compressed file to: " + OUTPUT_FILE);

        } catch (IOException e) {
            System.err.println("❌ Failed to convert file: " + e.getMessage());
        }
    }

    public static void splitTicks() {
        try {
            TickSplitter.splitTickFileBySymbolId(TRAINING_INPUT_FILE, TRAINING_OUTPUT_DIR);
            System.out.println("✅ Successfully split ticks by symbol ID.");
        } catch (Exception e) {
            System.err.println("❌ Failed to split ticks: " + e.getMessage());
        }
    }

    public static void triggerRetraining()
    {
        convertToAllTicksJson();
        splitTicks();
        BackTesterMLDriven2.runBacktest();
        clearMarketDataFile();

    }
}
