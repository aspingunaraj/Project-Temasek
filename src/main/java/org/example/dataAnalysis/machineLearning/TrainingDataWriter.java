package org.example.dataAnalysis.machineLearning;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.tribuo.classification.Label;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TrainingDataWriter {

    private static final String FILE_PATH = "src/main/java/org/example/dataAnalysis/machineLearning/training-data.json";
    private static final long MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024; // 2 MB
    private static final ObjectMapper mapper = new ObjectMapper();

    // Represent each training example as a map of features + label
    public static class TrainingExample {
        public Map<String, Double> features;
        public String label;

        public TrainingExample() {} // Required by Jackson

        public TrainingExample(Map<String, Double> features, String label) {
            this.features = features;
            this.label = label;
        }
    }

    public static void appendTrainingExample(TrainingExample example) {
        try {
            File file = new File(FILE_PATH);
            List<TrainingExample> examples;

            // Step 1: Load existing data or create new list
            if (file.exists()) {
                examples = mapper.readValue(file, new TypeReference<>() {});
            } else {
                examples = new LinkedList<>();
            }

            // Step 2: Add new example
            examples.add(example);

            // Step 3: Ensure size < 2 MB (by trimming from start)
            while (true) {
                mapper.writeValue(file, examples);
                if (Files.size(file.toPath()) <= MAX_FILE_SIZE_BYTES) break;
                if (!examples.isEmpty()) examples.remove(0); // Remove oldest
                else break;
            }

            System.out.println("✅ Training example saved. Total records: " + examples.size());

        } catch (IOException e) {
            System.err.println("❌ Failed to write training data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Utility to bulk append
    public static void appendBatch(List<TrainingExample> newExamples) {
        for (TrainingExample ex : newExamples) {
            appendTrainingExample(ex);
        }
    }
}
