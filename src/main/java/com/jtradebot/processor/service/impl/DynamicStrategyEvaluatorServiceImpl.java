package com.jtradebot.processor.service.impl;

import com.jtradebot.processor.model.DynamicFlattenedIndicators;
import com.jtradebot.processor.model.DynamicIndicatorConfig;
import com.jtradebot.processor.model.StrategyEvaluationResult;
import com.jtradebot.processor.service.DynamicStrategyEvaluatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicStrategyEvaluatorServiceImpl implements DynamicStrategyEvaluatorService {

    @Override
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

    @Override
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

    @Override
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

    @Override
    public double calculateConfidenceScore(DynamicFlattenedIndicators flattenedIndicators, DynamicIndicatorConfig config, String strategyType) {
        // This method is deprecated in favor of category-based evaluation
        return 0.0;
    }





    private double calculateStrengthMultiplier(DynamicFlattenedIndicators flattenedIndicators, String strategyType) {
        double multiplier = 1.0;
        
        // Check volume surge strength
        Double volumeMultiplier = flattenedIndicators.getNumericIndicator("volume_5min_multiplier");
        if (volumeMultiplier != null && volumeMultiplier > 3.0) {
            multiplier *= 1.2; // 20% bonus for strong volume surge
        }
        
        // Check RSI extremes
        Double rsiValue = flattenedIndicators.getNumericIndicator("rsi_5min_value");
        if (rsiValue != null) {
            if ("CALL".equals(strategyType) && rsiValue > 70) {
                multiplier *= 1.1; // 10% bonus for overbought RSI in CALL
            } else if ("PUT".equals(strategyType) && rsiValue < 30) {
                multiplier *= 1.1; // 10% bonus for oversold RSI in PUT
            }
        }
        
        return multiplier;
    }

    private double calculateTimeframeAlignmentBonus(DynamicFlattenedIndicators flattenedIndicators, String strategyType) {
        double bonus = 1.0;
        
        // Check if all timeframes are aligned
        List<String> timeframes = Arrays.asList("1min", "5min", "15min");
        int alignedTimeframes = 0;
        
        for (String timeframe : timeframes) {
            String conditionKey = strategyType.equals("CALL") ? 
                "ema_" + timeframe + "_crossover" : 
                "ema_" + timeframe + "_crossdown";
            
            Boolean conditionMet = flattenedIndicators.getBooleanIndicator(conditionKey);
            if (conditionMet != null && conditionMet) {
                alignedTimeframes++;
            }
        }
        
        // Apply bonus for timeframe alignment
        if (alignedTimeframes == 3) {
            bonus *= 1.3; // 30% bonus for all timeframes aligned
        } else if (alignedTimeframes == 2) {
            bonus *= 1.1; // 10% bonus for 2 timeframes aligned
        }
        
        return bonus;
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
