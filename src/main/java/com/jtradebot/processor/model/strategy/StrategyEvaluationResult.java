package com.jtradebot.processor.model.strategy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyEvaluationResult {
    
    private String recommendedStrategy; // CALL, PUT, or NO_TRADE
    private double confidenceScore; // 0.0 to 1.0
    private String reasoning;
    private Map<String, Object> evaluationDetails;
    private List<String> satisfiedConditions;
    private List<String> unsatisfiedConditions;
    private boolean isTradeRecommended;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallStrategyEvaluation {
        private boolean isCallRecommended;
        private double callConfidence;
        private List<String> satisfiedCallConditions;
        private List<String> unsatisfiedCallConditions;
        private String callReasoning;
        private Map<String, Object> callDetails;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PutStrategyEvaluation {
        private boolean isPutRecommended;
        private double putConfidence;
        private List<String> satisfiedPutConditions;
        private List<String> unsatisfiedPutConditions;
        private String putReasoning;
        private Map<String, Object> putDetails;
    }
}
