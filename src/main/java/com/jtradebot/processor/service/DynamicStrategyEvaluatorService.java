package com.jtradebot.processor.service;

import com.jtradebot.processor.model.indicator.DynamicFlattenedIndicators;
import com.jtradebot.processor.model.indicator.DynamicIndicatorConfig;
import com.jtradebot.processor.model.strategy.StrategyEvaluationResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicStrategyEvaluatorService {


    public StrategyEvaluationResult evaluateStrategy(DynamicFlattenedIndicators flattenedIndicators, DynamicIndicatorConfig config) {
        log.info("Evaluating strategy for instrument: {}", flattenedIndicators.getInstrumentToken());
        
        // Evaluate both CALL and PUT strategies
        StrategyEvaluationResult.CallStrategyEvaluation callEvaluation = evaluateCallStrategy(flattenedIndicators, config);
        StrategyEvaluationResult.PutStrategyEvaluation putEvaluation = evaluatePutStrategy(flattenedIndicators, config);
        
        // Determine the best strategy
        String recommendedStrategy = "NO_TRADE";
        double confidenceScore = 0.0;
        String reasoning = "No clear trading signal";
        boolean isTradeRecommended = false;
        
        if (callEvaluation.isCallRecommended() && putEvaluation.isPutRecommended()) {
            // Both strategies are recommended, choose the one with higher confidence
            if (callEvaluation.getCallConfidence() > putEvaluation.getPutConfidence()) {
                recommendedStrategy = "CALL";
                confidenceScore = callEvaluation.getCallConfidence();
                reasoning = "CALL strategy recommended with higher confidence than PUT";
                isTradeRecommended = true;
            } else {
                recommendedStrategy = "PUT";
                confidenceScore = putEvaluation.getPutConfidence();
                reasoning = "PUT strategy recommended with higher confidence than CALL";
                isTradeRecommended = true;
            }
        } else if (callEvaluation.isCallRecommended()) {
            recommendedStrategy = "CALL";
            confidenceScore = callEvaluation.getCallConfidence();
            reasoning = callEvaluation.getCallReasoning();
            isTradeRecommended = true;
        } else if (putEvaluation.isPutRecommended()) {
            recommendedStrategy = "PUT";
            confidenceScore = putEvaluation.getPutConfidence();
            reasoning = putEvaluation.getPutReasoning();
            isTradeRecommended = true;
        }
        
        // Combine evaluation details
        Map<String, Object> evaluationDetails = new HashMap<>();
        evaluationDetails.put("callEvaluation", callEvaluation);
        evaluationDetails.put("putEvaluation", putEvaluation);
        
        // Combine satisfied and unsatisfied conditions
        List<String> satisfiedConditions = new ArrayList<>();
        List<String> unsatisfiedConditions = new ArrayList<>();
        
        if (callEvaluation.getSatisfiedCallConditions() != null) {
            satisfiedConditions.addAll(callEvaluation.getSatisfiedCallConditions());
        }
        if (putEvaluation.getSatisfiedPutConditions() != null) {
            satisfiedConditions.addAll(putEvaluation.getSatisfiedPutConditions());
        }
        if (callEvaluation.getUnsatisfiedCallConditions() != null) {
            unsatisfiedConditions.addAll(callEvaluation.getUnsatisfiedCallConditions());
        }
        if (putEvaluation.getUnsatisfiedPutConditions() != null) {
            unsatisfiedConditions.addAll(putEvaluation.getUnsatisfiedPutConditions());
        }
        
        return StrategyEvaluationResult.builder()
                .recommendedStrategy(recommendedStrategy)
                .confidenceScore(confidenceScore)
                .reasoning(reasoning)
                .evaluationDetails(evaluationDetails)
                .satisfiedConditions(satisfiedConditions)
                .unsatisfiedConditions(unsatisfiedConditions)
                .isTradeRecommended(isTradeRecommended)
                .build();
    }


    public StrategyEvaluationResult.CallStrategyEvaluation evaluateCallStrategy(DynamicFlattenedIndicators flattenedIndicators, DynamicIndicatorConfig config) {
        log.debug("Evaluating CALL strategy for instrument: {}", flattenedIndicators.getInstrumentToken());
        
        Map<String, DynamicIndicatorConfig.Category> categories = config.getCallConditions().getCategories();
        int minCategoriesRequired = config.getCallConditions().getMinCategoriesRequired();
        
        List<String> satisfiedConditions = new ArrayList<>();
        List<String> unsatisfiedConditions = new ArrayList<>();
        Map<String, Boolean> categoryResults = new HashMap<>();
        Map<String, List<String>> categorySatisfiedIndicators = new HashMap<>();
        
        // Check each category
        for (Map.Entry<String, DynamicIndicatorConfig.Category> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            DynamicIndicatorConfig.Category category = entry.getValue();
            
            List<String> categorySatisfied = new ArrayList<>();
            int categorySatisfiedCount = 0;
            
            // Check each indicator in the category
            for (String indicator : category.getIndicators()) {
                Boolean conditionMet = flattenedIndicators.getBooleanIndicator(indicator);
                if (conditionMet != null && conditionMet) {
                    categorySatisfied.add(indicator);
                    categorySatisfiedCount++;
                    satisfiedConditions.add(indicator);
                } else {
                    unsatisfiedConditions.add(indicator);
                }
            }
            
            // Check if category requirement is met
            boolean categoryMet = categorySatisfiedCount >= category.getMinRequired();
            categoryResults.put(categoryName, categoryMet);
            categorySatisfiedIndicators.put(categoryName, categorySatisfied);
        }
        
        // Determine if CALL is recommended (at least minCategoriesRequired categories must be satisfied)
        int satisfiedCategories = (int) categoryResults.values().stream().filter(Boolean::booleanValue).count();
        boolean isCallRecommended = satisfiedCategories >= minCategoriesRequired;
        
        // Calculate confidence score
        double callConfidence = calculateCategoryBasedConfidence(categoryResults, categorySatisfiedIndicators, categories);
        
        // Generate reasoning
        String callReasoning = generateCategoryBasedReasoning(categoryResults, categorySatisfiedIndicators, categories, 
                                                             satisfiedCategories, minCategoriesRequired, "CALL");
        
        // Create evaluation details
        Map<String, Object> callDetails = new HashMap<>();
        callDetails.put("satisfiedCategories", satisfiedCategories);
        callDetails.put("totalCategories", categories.size());
        callDetails.put("minCategoriesRequired", minCategoriesRequired);
        callDetails.put("categoryResults", categoryResults);
        callDetails.put("categorySatisfiedIndicators", categorySatisfiedIndicators);
        callDetails.put("confidenceScore", callConfidence);
        
        return StrategyEvaluationResult.CallStrategyEvaluation.builder()
                .isCallRecommended(isCallRecommended)
                .callConfidence(callConfidence)
                .satisfiedCallConditions(satisfiedConditions)
                .unsatisfiedCallConditions(unsatisfiedConditions)
                .callReasoning(callReasoning)
                .callDetails(callDetails)
                .build();
    }


    public StrategyEvaluationResult.PutStrategyEvaluation evaluatePutStrategy(DynamicFlattenedIndicators flattenedIndicators, DynamicIndicatorConfig config) {
        log.debug("Evaluating PUT strategy for instrument: {}", flattenedIndicators.getInstrumentToken());
        
        Map<String, DynamicIndicatorConfig.Category> categories = config.getPutConditions().getCategories();
        int minCategoriesRequired = config.getPutConditions().getMinCategoriesRequired();
        
        List<String> satisfiedConditions = new ArrayList<>();
        List<String> unsatisfiedConditions = new ArrayList<>();
        Map<String, Boolean> categoryResults = new HashMap<>();
        Map<String, List<String>> categorySatisfiedIndicators = new HashMap<>();
        
        // Check each category
        for (Map.Entry<String, DynamicIndicatorConfig.Category> entry : categories.entrySet()) {
            String categoryName = entry.getKey();
            DynamicIndicatorConfig.Category category = entry.getValue();
            
            List<String> categorySatisfied = new ArrayList<>();
            int categorySatisfiedCount = 0;
            
            // Check each indicator in the category
            for (String indicator : category.getIndicators()) {
                Boolean conditionMet = flattenedIndicators.getBooleanIndicator(indicator);
                if (conditionMet != null && conditionMet) {
                    categorySatisfied.add(indicator);
                    categorySatisfiedCount++;
                    satisfiedConditions.add(indicator);
                } else {
                    unsatisfiedConditions.add(indicator);
                }
            }
            
            // Check if category requirement is met
            boolean categoryMet = categorySatisfiedCount >= category.getMinRequired();
            categoryResults.put(categoryName, categoryMet);
            categorySatisfiedIndicators.put(categoryName, categorySatisfied);
        }
        
        // Determine if PUT is recommended (at least minCategoriesRequired categories must be satisfied)
        int satisfiedCategories = (int) categoryResults.values().stream().filter(Boolean::booleanValue).count();
        boolean isPutRecommended = satisfiedCategories >= minCategoriesRequired;
        
        // Calculate confidence score
        double putConfidence = calculateCategoryBasedConfidence(categoryResults, categorySatisfiedIndicators, categories);
        
        // Generate reasoning
        String putReasoning = generateCategoryBasedReasoning(categoryResults, categorySatisfiedIndicators, categories, 
                                                            satisfiedCategories, minCategoriesRequired, "PUT");
        
        // Create evaluation details
        Map<String, Object> putDetails = new HashMap<>();
        putDetails.put("satisfiedCategories", satisfiedCategories);
        putDetails.put("totalCategories", categories.size());
        putDetails.put("minCategoriesRequired", minCategoriesRequired);
        putDetails.put("categoryResults", categoryResults);
        putDetails.put("categorySatisfiedIndicators", categorySatisfiedIndicators);
        putDetails.put("confidenceScore", putConfidence);
        
        return StrategyEvaluationResult.PutStrategyEvaluation.builder()
                .isPutRecommended(isPutRecommended)
                .putConfidence(putConfidence)
                .satisfiedPutConditions(satisfiedConditions)
                .unsatisfiedPutConditions(unsatisfiedConditions)
                .putReasoning(putReasoning)
                .putDetails(putDetails)
                .build();
    }


    private double calculateCategoryBasedConfidence(Map<String, Boolean> categoryResults, Map<String, List<String>> categorySatisfiedIndicators, Map<String, DynamicIndicatorConfig.Category> categories) {
        double confidence = 0.0;
        int totalCategories = categoryResults.size();
        int satisfiedCategories = (int) categoryResults.values().stream().filter(Boolean::booleanValue).count();

        if (totalCategories == 0) {
            return 0.0;
        }

        // Base confidence is the ratio of satisfied categories
        confidence = (double) satisfiedCategories / totalCategories;

        // Apply strength multiplier for each category
        for (Map.Entry<String, Boolean> entry : categoryResults.entrySet()) {
            String categoryName = entry.getKey();
            boolean categoryMet = entry.getValue();
            DynamicIndicatorConfig.Category category = categories.get(categoryName);

            if (category != null && categoryMet) {
                // Give bonus for each satisfied category
                confidence *= 1.1; // 10% bonus per satisfied category
            }
        }

        // Cap confidence at 1.0
        confidence = Math.min(1.0, confidence);

        log.debug("Category-based confidence calculation: base={}, satisfiedCategories={}, final={}",
                (double) satisfiedCategories / totalCategories, satisfiedCategories, confidence);

        return confidence;
    }

    private String generateCategoryBasedReasoning(Map<String, Boolean> categoryResults, Map<String, List<String>> categorySatisfiedIndicators, Map<String, DynamicIndicatorConfig.Category> categories, 
                                                  int satisfiedCategories, int minCategoriesRequired, String strategyType) {
        StringBuilder reasoning = new StringBuilder();
        boolean isRecommended = satisfiedCategories >= minCategoriesRequired;

        if (isRecommended) {
            reasoning.append("Strategy recommended. ");
            reasoning.append("Satisfied ").append(satisfiedCategories).append(" out of ").append(categoryResults.size()).append(" categories. ");
            reasoning.append("Confidence: ").append(String.format("%.2f", calculateCategoryBasedConfidence(categoryResults, categorySatisfiedIndicators, categories) * 100)).append("%. ");

            if (strategyType.equals("CALL")) {
                reasoning.append("Key bullish signals: ");
            } else {
                reasoning.append("Key bearish signals: ");
            }
            List<String> allSatisfiedIndicators = new ArrayList<>();
            for (List<String> satisfied : categorySatisfiedIndicators.values()) {
                allSatisfiedIndicators.addAll(satisfied);
            }
            reasoning.append(String.join(", ", allSatisfiedIndicators.subList(0, Math.min(3, allSatisfiedIndicators.size()))));
        } else {
            reasoning.append("Strategy not recommended. ");
            reasoning.append("Only ").append(satisfiedCategories).append(" categories met, need ").append(minCategoriesRequired).append(". ");
            List<String> allUnsatisfiedIndicators = new ArrayList<>();
            for (List<String> unsatisfied : categorySatisfiedIndicators.values()) {
                allUnsatisfiedIndicators.addAll(unsatisfied);
            }
            reasoning.append("Missing: ").append(String.join(", ", allUnsatisfiedIndicators.subList(0, Math.min(3, allUnsatisfiedIndicators.size()))));
        }
        return reasoning.toString();
    }
}
