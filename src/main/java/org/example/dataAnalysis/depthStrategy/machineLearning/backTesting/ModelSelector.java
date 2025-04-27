// ModelSelector.java
package org.example.dataAnalysis.depthStrategy.machineLearning.backTesting;

import weka.classifiers.Classifier;
import weka.classifiers.functions.SMO;
import weka.classifiers.functions.Logistic;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.REPTree;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.meta.Bagging;
import weka.classifiers.meta.LogitBoost;

/**
 * Utility class to dynamically select machine learning models.
 */
public class ModelSelector {

    public enum ModelType {
        RANDOM_FOREST,
        ADA_BOOST,
        BAGGING,
        LOGIT_BOOST,
        SVM,
        NAIVE_BAYES,
        LOGISTIC_REGRESSION,
        J48_TREE,
        REP_TREE,
        SIMPLE_CART
    }

    /**
     * Returns a new untrained classifier instance based on selected model type.
     *
     * @param modelType The model to instantiate.
     * @return Classifier instance
     * @throws Exception if creation fails
     */
    public static Classifier getModel(ModelType modelType) throws Exception {
        switch (modelType) {
            case RANDOM_FOREST:
                RandomForest rf = new RandomForest();
                rf.setNumIterations(100);
                return rf;

            case ADA_BOOST:
                AdaBoostM1 adaBoost = new AdaBoostM1();
                adaBoost.setNumIterations(50);
                return adaBoost;

            case BAGGING:
                Bagging bagging = new Bagging();
                bagging.setNumIterations(50);
                return bagging;

            case LOGIT_BOOST:
                LogitBoost logitBoost = new LogitBoost();
                logitBoost.setNumIterations(50);
                return logitBoost;

            case SVM:
                SMO smo = new SMO();
                return smo;

            case NAIVE_BAYES:
                NaiveBayes nb = new NaiveBayes();
                return nb;

            case LOGISTIC_REGRESSION:
                Logistic logistic = new Logistic();
                return logistic;

            case J48_TREE:
                J48 j48 = new J48();
                j48.setConfidenceFactor(0.25f);
                j48.setMinNumObj(5);
                return j48;

            case REP_TREE:
                REPTree repTree = new REPTree();
                repTree.setMinNum(5);
                return repTree;



            default:
                throw new IllegalArgumentException("Unsupported Model Type: " + modelType);
        }
    }
}
