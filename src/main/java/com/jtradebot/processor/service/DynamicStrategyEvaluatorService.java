package com.jtradebot.processor.service;

import com.jtradebot.processor.model.DynamicFlattenedIndicators;
import com.jtradebot.processor.model.DynamicIndicatorConfig;
import com.jtradebot.processor.model.StrategyEvaluationResult;

public interface DynamicStrategyEvaluatorService {
    
    /**
     * Evaluates trading strategy based on dynamic indicators
     * @param flattenedIndicators The flattened indicators
     * @param config The indicator configuration
     * @return Strategy evaluation result
     */
    StrategyEvaluationResult evaluateStrategy(DynamicFlattenedIndicators flattenedIndicators, DynamicIndicatorConfig config);
    
    /**
     * Determines if conditions are met for CALL strategy
     * @param flattenedIndicators The flattened indicators
     * @param config The indicator configuration
     * @return Call strategy evaluation
     */
    StrategyEvaluationResult.CallStrategyEvaluation evaluateCallStrategy(DynamicFlattenedIndicators flattenedIndicators, DynamicIndicatorConfig config);
    
    /**
     * Determines if conditions are met for PUT strategy
     * @param flattenedIndicators The flattened indicators
     * @param config The indicator configuration
     * @return Put strategy evaluation
     */
    StrategyEvaluationResult.PutStrategyEvaluation evaluatePutStrategy(DynamicFlattenedIndicators flattenedIndicators, DynamicIndicatorConfig config);
    
    /**
     * Calculates strategy confidence score
     * @param flattenedIndicators The flattened indicators
     * @param config The indicator configuration
     * @param strategyType The strategy type (CALL/PUT)
     * @return Confidence score between 0.0 and 1.0
     */
    double calculateConfidenceScore(DynamicFlattenedIndicators flattenedIndicators, DynamicIndicatorConfig config, String strategyType);
}
