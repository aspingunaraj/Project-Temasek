package org.example.dataAnalysis.machineLearning;

import org.example.dataAnalysis.machineLearning.TickAdapter.LabeledTick;
import org.example.websocket.model.Tick;
import org.tribuo.*;
import org.tribuo.classification.Label;
import org.tribuo.classification.LabelFactory;
import org.tribuo.classification.evaluation.LabelEvaluation;
import org.tribuo.classification.evaluation.LabelEvaluator;
import org.tribuo.classification.sgd.linear.LinearSGDTrainer;
import org.tribuo.classification.sgd.objectives.LogMulticlass;
import org.tribuo.datasource.ListDataSource;
import org.tribuo.evaluation.TrainTestSplitter;
import org.tribuo.math.optimisers.AdaGrad;
import org.tribuo.provenance.SimpleDataSourceProvenance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.time.OffsetDateTime;
import java.util.List;

public class SignalModelTrainer {

    public static void main(String[] args) {
        try {
            LabelFactory labelFactory = new LabelFactory();

            // 1. Simulate a longer history of ticks (e.g., 300)
            List<Tick> tickHistory = TickAdapter.generateSimulatedTicks(300);

            // 2. Label the data based on future price changes
            List<LabeledTick> labeledTicks = TickAdapter.labelTicks(tickHistory, 0.3);

            // 3. Convert to examples using historical window
            List<Example<Label>> examples = TickAdapter.convertToExamplesWithHistory(labeledTicks, tickHistory, labelFactory);

            // 4. Prepare DataSource with provenance info
            DataSource<Label> dataSource = new ListDataSource<>(
                    examples,
                    labelFactory,
                    new SimpleDataSourceProvenance("TickData-Historical", OffsetDateTime.now(), labelFactory)
            );

            // 5. Train-test split (70/30)
            TrainTestSplitter<Label> splitter = new TrainTestSplitter<>(dataSource, 0.7, 42L);
            MutableDataset<Label> train = new MutableDataset<>(splitter.getTrain());
            MutableDataset<Label> test = new MutableDataset<>(splitter.getTest());

            // 6. Trainer setup
            Trainer<Label> trainer = new LinearSGDTrainer(
                    new LogMulticlass(),
                    new AdaGrad(0.1),
                    10,
                    train.size() / 4,
                    1,
                    1L
            );

            // 7. Train the model
            Model<Label> model = trainer.train(train);

            // 8. Evaluate on test data
            LabelEvaluation evaluation = new LabelEvaluator().evaluate(model, test);
            System.out.println("Evaluation:\n" + evaluation);
            System.out.println("Confusion Matrix:\n" + evaluation.getConfusionMatrix());
            System.out.printf("Accuracy: %.2f%%%n", evaluation.accuracy() * 100);

            // 9. Save the model
            File modelDir = new File("src/org/example/dataAnalysis");
            if (!modelDir.exists()) modelDir.mkdirs();

            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream("src/main/java/org/example/dataAnalysis/machineLearning/signal-model-v1.ser"))) {
                oos.writeObject(model);
                System.out.println("Model saved to signal-model-v1.ser");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void trainFromTickHistory(List<Tick> history) {
        try {
            if (history.size() < 300) return;

            LabelFactory factory = new LabelFactory();
            List<TickAdapter.LabeledTick> labeledTicks = TickAdapter.labelTicks(history, 0.3);
            List<Example<Label>> examples = TickAdapter.convertToExamplesWithHistory(labeledTicks, history, factory);

            DataSource<Label> dataSource = new ListDataSource<>(
                    examples,
                    factory,
                    new SimpleDataSourceProvenance("TickData-Historical", OffsetDateTime.now(), factory)
            );

            MutableDataset<Label> dataset = new MutableDataset<>(dataSource);
            Trainer<Label> trainer = new LinearSGDTrainer(
                    new LogMulticlass(),
                    new AdaGrad(0.1),
                    10,
                    dataset.size() / 4,
                    1,
                    1L
            );

            Model<Label> model = trainer.train(dataset);
            System.out.println("✅ Model trained from tick history (" + history.size() + " ticks)");

        } catch (Exception e) {
            System.err.println("❌ Error during training: " + e.getMessage());
            e.printStackTrace();
        }
    }



}
