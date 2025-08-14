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
        
        List<String> requiredCallConditions = getCallConditions(config);
        List<String> satisfiedConditions = new ArrayList<>();
        List<String> unsatisfiedConditions = new ArrayList<>();
        
        // Check each required condition
        for (String condition : requiredCallConditions) {
            Boolean conditionMet = flattenedIndicators.getBooleanIndicator(condition);
            if (conditionMet != null && conditionMet) {
                satisfiedConditions.add(condition);
            } else {
                unsatisfiedConditions.add(condition);
            }
        }
        
        // Determine if CALL is recommended
        int minRequiredCount = getMinRequiredCount(config, "callConditions");
        boolean isCallRecommended = satisfiedConditions.size() >= minRequiredCount;
        
        // Calculate confidence score
        double callConfidence = calculateConfidenceScore(flattenedIndicators, config, "CALL");
        
        // Generate reasoning
        String callReasoning = generateCallReasoning(satisfiedConditions, unsatisfiedConditions, minRequiredCount, callConfidence);
        
        // Create evaluation details
        Map<String, Object> callDetails = new HashMap<>();
        callDetails.put("satisfiedCount", satisfiedConditions.size());
        callDetails.put("totalRequired", requiredCallConditions.size());
        callDetails.put("minRequired", minRequiredCount);
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
        
        List<String> requiredPutConditions = getPutConditions(config);
        List<String> satisfiedConditions = new ArrayList<>();
        List<String> unsatisfiedConditions = new ArrayList<>();
        
        // Check each required condition
        for (String condition : requiredPutConditions) {
            Boolean conditionMet = flattenedIndicators.getBooleanIndicator(condition);
            if (conditionMet != null && conditionMet) {
                satisfiedConditions.add(condition);
            } else {
                unsatisfiedConditions.add(condition);
            }
        }
        
        // Determine if PUT is recommended
        int minRequiredCount = getMinRequiredCount(config, "putConditions");
        boolean isPutRecommended = satisfiedConditions.size() >= minRequiredCount;
        
        // Calculate confidence score
        double putConfidence = calculateConfidenceScore(flattenedIndicators, config, "PUT");
        
        // Generate reasoning
        String putReasoning = generatePutReasoning(satisfiedConditions, unsatisfiedConditions, minRequiredCount, putConfidence);
        
        // Create evaluation details
        Map<String, Object> putDetails = new HashMap<>();
        putDetails.put("satisfiedCount", satisfiedConditions.size());
        putDetails.put("totalRequired", requiredPutConditions.size());
        putDetails.put("minRequired", minRequiredCount);
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
        List<String> requiredConditions;
        
        if ("CALL".equals(strategyType)) {
            requiredConditions = getCallConditions(config);
        } else if ("PUT".equals(strategyType)) {
            requiredConditions = getPutConditions(config);
        } else {
            return 0.0;
        }
        
        if (requiredConditions.isEmpty()) {
            return 0.0;
        }
        
        // Count satisfied conditions
        long satisfiedCount = requiredConditions.stream()
                .map(flattenedIndicators::getBooleanIndicator)
                .filter(Objects::nonNull)
                .filter(Boolean::booleanValue)
                .count();
        
        // Calculate base confidence as ratio of satisfied conditions
        double baseConfidence = (double) satisfiedCount / requiredConditions.size();
        
        // Apply additional confidence factors based on indicator strength
        double strengthMultiplier = calculateStrengthMultiplier(flattenedIndicators, strategyType);
        
        // Apply timeframe alignment bonus
        double timeframeBonus = calculateTimeframeAlignmentBonus(flattenedIndicators, strategyType);
        
        // Calculate final confidence score (capped at 1.0)
        double finalConfidence = Math.min(1.0, baseConfidence * strengthMultiplier * timeframeBonus);
        
        log.debug("Confidence calculation for {}: base={}, strength={}, timeframe={}, final={}", 
                strategyType, baseConfidence, strengthMultiplier, timeframeBonus, finalConfidence);
        
        return finalConfidence;
    }

    // Helper methods
    private List<String> getCallConditions(DynamicIndicatorConfig config) {
        if (config.getCallConditions() != null && config.getCallConditions().getRequiredIndicators() != null) {
            return config.getCallConditions().getRequiredIndicators();
        }
        return new ArrayList<>();
    }

    private List<String> getPutConditions(DynamicIndicatorConfig config) {
        if (config.getPutConditions() != null && config.getPutConditions().getRequiredIndicators() != null) {
            return config.getPutConditions().getRequiredIndicators();
        }
        return new ArrayList<>();
    }

    private int getMinRequiredCount(DynamicIndicatorConfig config, String conditionType) {
        if ("callConditions".equals(conditionType) && config.getCallConditions() != null) {
            return config.getCallConditions().getMinRequiredCount();
        } else if ("putConditions".equals(conditionType) && config.getPutConditions() != null) {
            return config.getPutConditions().getMinRequiredCount();
        }
        return 10; // Default minimum
    }

    private String generateCallReasoning(List<String> satisfiedConditions, List<String> unsatisfiedConditions, 
                                       int minRequiredCount, double confidence) {
        StringBuilder reasoning = new StringBuilder();
        
        if (satisfiedConditions.size() >= minRequiredCount) {
            reasoning.append("CALL strategy recommended. ");
            reasoning.append("Satisfied ").append(satisfiedConditions.size()).append(" out of ").append(satisfiedConditions.size() + unsatisfiedConditions.size()).append(" conditions. ");
            reasoning.append("Confidence: ").append(String.format("%.2f", confidence * 100)).append("%. ");
            
            if (!satisfiedConditions.isEmpty()) {
                reasoning.append("Key bullish signals: ").append(String.join(", ", satisfiedConditions.subList(0, Math.min(3, satisfiedConditions.size()))));
            }
        } else {
            reasoning.append("CALL strategy not recommended. ");
            reasoning.append("Only ").append(satisfiedConditions.size()).append(" conditions met, need ").append(minRequiredCount).append(". ");
            reasoning.append("Missing: ").append(String.join(", ", unsatisfiedConditions.subList(0, Math.min(3, unsatisfiedConditions.size()))));
        }
        
        return reasoning.toString();
    }

    private String generatePutReasoning(List<String> satisfiedConditions, List<String> unsatisfiedConditions, 
                                      int minRequiredCount, double confidence) {
        StringBuilder reasoning = new StringBuilder();
        
        if (satisfiedConditions.size() >= minRequiredCount) {
            reasoning.append("PUT strategy recommended. ");
            reasoning.append("Satisfied ").append(satisfiedConditions.size()).append(" out of ").append(satisfiedConditions.size() + unsatisfiedConditions.size()).append(" conditions. ");
            reasoning.append("Confidence: ").append(String.format("%.2f", confidence * 100)).append("%. ");
            
            if (!satisfiedConditions.isEmpty()) {
                reasoning.append("Key bearish signals: ").append(String.join(", ", satisfiedConditions.subList(0, Math.min(3, satisfiedConditions.size()))));
            }
        } else {
            reasoning.append("PUT strategy not recommended. ");
            reasoning.append("Only ").append(satisfiedConditions.size()).append(" conditions met, need ").append(minRequiredCount).append(". ");
            reasoning.append("Missing: ").append(String.join(", ", unsatisfiedConditions.subList(0, Math.min(3, unsatisfiedConditions.size()))));
        }
        
        return reasoning.toString();
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
}
