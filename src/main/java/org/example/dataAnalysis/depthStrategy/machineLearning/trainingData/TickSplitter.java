package org.example.dataAnalysis.depthStrategy.machineLearning.trainingData;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class TickSplitter {

    private static final String INPUT_FILE = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/compressedTickDump.json";
    private static final String OUTPUT_DIR = "src/main/java/org/example/dataAnalysis/depthStrategy/machineLearning/trainingData/";

    public static void main(String[] args) {
        splitTickFileBySymbolId(INPUT_FILE, OUTPUT_DIR);
    }

    public static void splitTickFileBySymbolId(String inputFile, String outputDirectory) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer();
        Map<Integer, BufferedWriter> writers = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(inputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                JsonNode node = mapper.readTree(line);
                int securityId = node.get("securityId").asInt();
                String outputFileName = outputDirectory + "compressedTickDump_" + securityId + ".json";

                BufferedWriter bw = writers.computeIfAbsent(securityId, id -> {
                    try {
                        return Files.newBufferedWriter(Paths.get(outputFileName), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
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
            // Close all writers
            for (BufferedWriter bw : writers.values()) {
                try {
                    bw.close();
                } catch (IOException e) {
                    // Log and continue
                    e.printStackTrace();
                }
            }
        }
    }
}
