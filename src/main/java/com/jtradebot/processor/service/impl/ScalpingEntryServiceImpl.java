package com.jtradebot.processor.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.model.*;
import com.jtradebot.processor.service.ScalpingEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScalpingEntryServiceImpl implements ScalpingEntryService {

    private final ObjectMapper objectMapper;

    @Override
    public ScalpingEntryDecision evaluateCallEntry(DynamicFlattenedIndicators flattenedIndicators, 
                                                 DynamicIndicatorConfig config, 
                                                 double currentPrice, 
                                                 LocalDateTime currentTime) {
        log.info("Evaluating CALL entry for instrument: {} at price: {}", 
                flattenedIndicators.getInstrumentToken(), currentPrice);
        
        try {
            // Load entry logic configuration
            ScalpingEntryLogic entryLogic = loadEntryLogic("rules/scalping-entry-config.json");
            if (entryLogic == null) {
                return createRejectionDecision("CALL", "Failed to load entry logic configuration", currentPrice, currentTime);
            }
            
            // Get CALL strategy configuration
            ScalpingEntryLogic.EntryConditions callConditions = entryLogic.getEntryConditions();
            ScalpingEntryLogic.RiskManagement riskManagement = entryLogic.getRiskManagement();
            ScalpingEntryLogic.EntryQuality entryQuality = entryLogic.getEntryQuality();
            
            // Validate mandatory conditions
            List<String> satisfiedMandatory = validateMandatoryConditions(flattenedIndicators, callConditions.getMandatoryConditions());
            List<String> unsatisfiedMandatory = getUnsatisfiedConditions(callConditions.getMandatoryConditions(), satisfiedMandatory);
            
            if (satisfiedMandatory.size() < callConditions.getMinMandatoryCount()) {
                String reason = String.format("Insufficient mandatory conditions: %d/%d satisfied", 
                        satisfiedMandatory.size(), callConditions.getMinMandatoryCount());
                return createRejectionDecision("CALL", reason, currentPrice, currentTime);
            }
            
            // Validate optional conditions
            List<String> satisfiedOptional = validateOptionalConditions(flattenedIndicators, callConditions.getOptionalConditions());
            List<String> unsatisfiedOptional = getUnsatisfiedConditions(callConditions.getOptionalConditions(), satisfiedOptional);
            
            if (satisfiedOptional.size() < callConditions.getMinOptionalCount()) {
                String reason = String.format("Insufficient optional conditions: %d/%d satisfied", 
                        satisfiedOptional.size(), callConditions.getMinOptionalCount());
                return createRejectionDecision("CALL", reason, currentPrice, currentTime);
            }
            
            // Calculate confidence score
            double confidenceScore = calculateCallConfidenceScore(flattenedIndicators, satisfiedMandatory, satisfiedOptional);
            
            if (confidenceScore < callConditions.getMinConfidenceScore()) {
                String reason = String.format("Insufficient confidence score: %.2f < %.2f", 
                        confidenceScore, callConditions.getMinConfidenceScore());
                return createRejectionDecision("CALL", reason, currentPrice, currentTime);
            }
            
            // Validate entry quality
            ScalpingEntryDecision.EntryQualityScore qualityScore = calculateEntryQuality(flattenedIndicators, "CALL", entryQuality);
            
            if (qualityScore.getOverallScore() < 0.7) {
                String reason = String.format("Poor entry quality: %.2f (Grade: %s)", 
                        qualityScore.getOverallScore(), qualityScore.getQualityGrade());
                return createRejectionDecision("CALL", reason, currentPrice, currentTime);
            }
            
            // Calculate position size and risk levels
            int positionSize = calculatePositionSize(100000, riskManagement.getMaxRiskPerTrade(), 
                                                   riskManagement.getStopLossPoints(), currentPrice);
            
            StopLossTargetLevels levels = calculateStopLossTarget(currentPrice, "CALL", 
                                                                riskManagement.getStopLossPoints(), 
                                                                riskManagement.getTargetPoints());
            
            // Create entry decision
            return ScalpingEntryDecision.builder()
                    .strategyType("CALL")
                    .shouldEnter(true)
                    .entryPrice(currentPrice)
                    .positionSize(positionSize)
                    .stopLossPrice(levels.getStopLossPrice())
                    .targetPrice(levels.getTargetPrice())
                    .confidenceScore(confidenceScore)
                    .entryReason(generateCallEntryReason(satisfiedMandatory, satisfiedOptional, confidenceScore, qualityScore))
                    .entryTime(currentTime)
                    .maxHoldingTimeMinutes(riskManagement.getMaxHoldingTimeMinutes())
                    .useTrailingStop(riskManagement.isUseTrailingStop())
                    .trailingStopPercentage(riskManagement.getTrailingStopPercentage())
                    .useBreakEven(riskManagement.isUseBreakEven())
                    .breakEvenTrigger(riskManagement.getBreakEvenTrigger())
                    .satisfiedConditions(satisfiedMandatory)
                    .unsatisfiedConditions(unsatisfiedMandatory)
                    .entryQuality(qualityScore)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error evaluating CALL entry", e);
            return createRejectionDecision("CALL", "Error during evaluation: " + e.getMessage(), currentPrice, currentTime);
        }
    }

    @Override
    public ScalpingEntryDecision evaluatePutEntry(DynamicFlattenedIndicators flattenedIndicators, 
                                                DynamicIndicatorConfig config, 
                                                double currentPrice, 
                                                LocalDateTime currentTime) {
        log.info("Evaluating PUT entry for instrument: {} at price: {}", 
                flattenedIndicators.getInstrumentToken(), currentPrice);
        
        try {
            // Load entry logic configuration
            ScalpingEntryLogic entryLogic = loadEntryLogic("rules/scalping-entry-config.json");
            if (entryLogic == null) {
                return createRejectionDecision("PUT", "Failed to load entry logic configuration", currentPrice, currentTime);
            }
            
            // Get PUT strategy configuration
            ScalpingEntryLogic.EntryConditions putConditions = entryLogic.getEntryConditions();
            ScalpingEntryLogic.RiskManagement riskManagement = entryLogic.getRiskManagement();
            ScalpingEntryLogic.EntryQuality entryQuality = entryLogic.getEntryQuality();
            
            // Validate mandatory conditions
            List<String> satisfiedMandatory = validateMandatoryConditions(flattenedIndicators, putConditions.getMandatoryConditions());
            List<String> unsatisfiedMandatory = getUnsatisfiedConditions(putConditions.getMandatoryConditions(), satisfiedMandatory);
            
            if (satisfiedMandatory.size() < putConditions.getMinMandatoryCount()) {
                String reason = String.format("Insufficient mandatory conditions: %d/%d satisfied", 
                        satisfiedMandatory.size(), putConditions.getMinMandatoryCount());
                return createRejectionDecision("PUT", reason, currentPrice, currentTime);
            }
            
            // Validate optional conditions
            List<String> satisfiedOptional = validateOptionalConditions(flattenedIndicators, putConditions.getOptionalConditions());
            List<String> unsatisfiedOptional = getUnsatisfiedConditions(putConditions.getOptionalConditions(), satisfiedOptional);
            
            if (satisfiedOptional.size() < putConditions.getMinOptionalCount()) {
                String reason = String.format("Insufficient optional conditions: %d/%d satisfied", 
                        satisfiedOptional.size(), putConditions.getMinOptionalCount());
                return createRejectionDecision("PUT", reason, currentPrice, currentTime);
            }
            
            // Calculate confidence score
            double confidenceScore = calculatePutConfidenceScore(flattenedIndicators, satisfiedMandatory, satisfiedOptional);
            
            if (confidenceScore < putConditions.getMinConfidenceScore()) {
                String reason = String.format("Insufficient confidence score: %.2f < %.2f", 
                        confidenceScore, putConditions.getMinConfidenceScore());
                return createRejectionDecision("PUT", reason, currentPrice, currentTime);
            }
            
            // Validate entry quality
            ScalpingEntryDecision.EntryQualityScore qualityScore = calculateEntryQuality(flattenedIndicators, "PUT", entryQuality);
            
            if (qualityScore.getOverallScore() < 0.7) {
                String reason = String.format("Poor entry quality: %.2f (Grade: %s)", 
                        qualityScore.getOverallScore(), qualityScore.getQualityGrade());
                return createRejectionDecision("PUT", reason, currentPrice, currentTime);
            }
            
            // Calculate position size and risk levels
            int positionSize = calculatePositionSize(100000, riskManagement.getMaxRiskPerTrade(), 
                                                   riskManagement.getStopLossPoints(), currentPrice);
            
            StopLossTargetLevels levels = calculateStopLossTarget(currentPrice, "PUT", 
                                                                riskManagement.getStopLossPoints(), 
                                                                riskManagement.getTargetPoints());
            
            // Create entry decision
            return ScalpingEntryDecision.builder()
                    .strategyType("PUT")
                    .shouldEnter(true)
                    .entryPrice(currentPrice)
                    .positionSize(positionSize)
                    .stopLossPrice(levels.getStopLossPrice())
                    .targetPrice(levels.getTargetPrice())
                    .confidenceScore(confidenceScore)
                    .entryReason(generatePutEntryReason(satisfiedMandatory, satisfiedOptional, confidenceScore, qualityScore))
                    .entryTime(currentTime)
                    .maxHoldingTimeMinutes(riskManagement.getMaxHoldingTimeMinutes())
                    .useTrailingStop(riskManagement.isUseTrailingStop())
                    .trailingStopPercentage(riskManagement.getTrailingStopPercentage())
                    .useBreakEven(riskManagement.isUseBreakEven())
                    .breakEvenTrigger(riskManagement.getBreakEvenTrigger())
                    .satisfiedConditions(satisfiedMandatory)
                    .unsatisfiedConditions(unsatisfiedMandatory)
                    .entryQuality(qualityScore)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error evaluating PUT entry", e);
            return createRejectionDecision("PUT", "Error during evaluation: " + e.getMessage(), currentPrice, currentTime);
        }
    }

    @Override
    public int calculatePositionSize(double accountBalance, double riskPerTrade, 
                                   double stopLossPoints, double currentPrice) {
        double riskAmount = accountBalance * (riskPerTrade / 100.0);
        double riskPerShare = stopLossPoints;
        int positionSize = (int) (riskAmount / riskPerShare);
        
        // Round down to nearest lot size (50 for Nifty)
        positionSize = (positionSize / 50) * 50;
        
        // Ensure minimum position size
        if (positionSize < 50) {
            positionSize = 50;
        }
        
        log.debug("Position size calculation: balance={}, risk={}%, stopLoss={}, size={}", 
                accountBalance, riskPerTrade, stopLossPoints, positionSize);
        
        return positionSize;
    }

    @Override
    public StopLossTargetLevels calculateStopLossTarget(double entryPrice, String strategyType, 
                                                      double stopLossPoints, double targetPoints) {
        double stopLossPrice, targetPrice;
        
        if ("CALL".equals(strategyType)) {
            stopLossPrice = entryPrice - stopLossPoints;
            targetPrice = entryPrice + targetPoints;
        } else {
            stopLossPrice = entryPrice + stopLossPoints;
            targetPrice = entryPrice - targetPoints;
        }
        
        double riskRewardRatio = targetPoints / stopLossPoints;
        
        return StopLossTargetLevels.builder()
                .entryPrice(entryPrice)
                .stopLossPrice(stopLossPrice)
                .targetPrice(targetPrice)
                .stopLossPoints(stopLossPoints)
                .targetPoints(targetPoints)
                .riskRewardRatio(riskRewardRatio)
                .strategyType(strategyType)
                .build();
    }

    @Override
    public MarketConditionValidation validateMarketConditions(LocalDateTime currentTime, 
                                                            double currentPrice, 
                                                            double bidPrice, 
                                                            double askPrice, 
                                                            long volume) {
        try {
            ScalpingEntryLogic entryLogic = loadEntryLogic("rules/scalping-entry-config.json");
            if (entryLogic == null) {
                return MarketConditionValidation.builder()
                        .isValid(false)
                        .rejectionReason("Failed to load entry logic configuration")
                        .validationTime(currentTime)
                        .build();
            }
            
            ScalpingEntryLogic.MarketConditions marketConditions = entryLogic.getMarketConditions();
            
            // Calculate spread
            double spread = askPrice - bidPrice;
            double spreadPercentage = (spread / currentPrice) * 100;
            
            // Check spread
            if (spreadPercentage > marketConditions.getMaxSpreadPercentage()) {
                return MarketConditionValidation.builder()
                        .isValid(false)
                        .spreadPercentage(spreadPercentage)
                        .rejectionReason("Spread too high: " + String.format("%.2f%%", spreadPercentage))
                        .validationTime(currentTime)
                        .build();
            }
            
            // Check volume
            if (volume < marketConditions.getMinLiquidityThreshold()) {
                return MarketConditionValidation.builder()
                        .isValid(false)
                        .volume(volume)
                        .rejectionReason("Insufficient liquidity: " + volume)
                        .validationTime(currentTime)
                        .build();
            }
            
            // Check time slots
            LocalTime time = currentTime.toLocalTime();
            for (String avoidSlot : marketConditions.getAvoidTimeSlots()) {
                if (isInTimeSlot(time, avoidSlot)) {
                    return MarketConditionValidation.builder()
                            .isValid(false)
                            .isAvoidTimeSlot(true)
                            .rejectionReason("In avoid time slot: " + avoidSlot)
                            .validationTime(currentTime)
                            .build();
                }
            }
            
            // Determine market session
            String marketSession = determineMarketSession(time);
            
            return MarketConditionValidation.builder()
                    .isValid(true)
                    .marketSession(marketSession)
                    .spreadPercentage(spreadPercentage)
                    .volume(volume)
                    .isLiquid(true)
                    .validationTime(currentTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error validating market conditions", e);
            return MarketConditionValidation.builder()
                    .isValid(false)
                    .rejectionReason("Error during validation: " + e.getMessage())
                    .validationTime(currentTime)
                    .build();
        }
    }

    @Override
    public ScalpingEntryLogic loadEntryLogic(String configPath) {
        try {
            ClassPathResource resource = new ClassPathResource(configPath);
            return objectMapper.readValue(resource.getInputStream(), ScalpingEntryLogic.class);
        } catch (IOException e) {
            log.error("Error loading entry logic configuration from: {}", configPath, e);
            return null;
        }
    }

    // Helper methods
    private List<String> validateMandatoryConditions(DynamicFlattenedIndicators flattenedIndicators, 
                                                   List<String> mandatoryConditions) {
        return mandatoryConditions.stream()
                .filter(condition -> {
                    Boolean value = flattenedIndicators.getBooleanIndicator(condition);
                    return value != null && value;
                })
                .collect(Collectors.toList());
    }

    private List<String> validateOptionalConditions(DynamicFlattenedIndicators flattenedIndicators, 
                                                  List<String> optionalConditions) {
        return optionalConditions.stream()
                .filter(condition -> {
                    Boolean value = flattenedIndicators.getBooleanIndicator(condition);
                    return value != null && value;
                })
                .collect(Collectors.toList());
    }

    private List<String> getUnsatisfiedConditions(List<String> allConditions, List<String> satisfiedConditions) {
        return allConditions.stream()
                .filter(condition -> !satisfiedConditions.contains(condition))
                .collect(Collectors.toList());
    }

    private double calculateCallConfidenceScore(DynamicFlattenedIndicators flattenedIndicators, 
                                              List<String> satisfiedMandatory, 
                                              List<String> satisfiedOptional) {
        double baseScore = (double) satisfiedMandatory.size() / 8.0; // 8 mandatory conditions
        double optionalBonus = (double) satisfiedOptional.size() / 6.0 * 0.2; // 20% bonus for optional
        
        // Volume surge bonus
        Double volumeMultiplier = flattenedIndicators.getNumericIndicator("volume_5min_multiplier");
        double volumeBonus = 0.0;
        if (volumeMultiplier != null && volumeMultiplier > 3.0) {
            volumeBonus = 0.1; // 10% bonus for strong volume
        }
        
        // RSI strength bonus
        Double rsiValue = flattenedIndicators.getNumericIndicator("rsi_5min_value");
        double rsiBonus = 0.0;
        if (rsiValue != null && rsiValue > 70) {
            rsiBonus = 0.1; // 10% bonus for strong RSI
        }
        
        return Math.min(1.0, baseScore + optionalBonus + volumeBonus + rsiBonus);
    }

    private double calculatePutConfidenceScore(DynamicFlattenedIndicators flattenedIndicators, 
                                             List<String> satisfiedMandatory, 
                                             List<String> satisfiedOptional) {
        double baseScore = (double) satisfiedMandatory.size() / 8.0; // 8 mandatory conditions
        double optionalBonus = (double) satisfiedOptional.size() / 6.0 * 0.2; // 20% bonus for optional
        
        // Volume surge bonus
        Double volumeMultiplier = flattenedIndicators.getNumericIndicator("volume_5min_multiplier");
        double volumeBonus = 0.0;
        if (volumeMultiplier != null && volumeMultiplier > 3.0) {
            volumeBonus = 0.1; // 10% bonus for strong volume
        }
        
        // RSI strength bonus
        Double rsiValue = flattenedIndicators.getNumericIndicator("rsi_5min_value");
        double rsiBonus = 0.0;
        if (rsiValue != null && rsiValue < 30) {
            rsiBonus = 0.1; // 10% bonus for strong RSI
        }
        
        return Math.min(1.0, baseScore + optionalBonus + volumeBonus + rsiBonus);
    }

    private ScalpingEntryDecision.EntryQualityScore calculateEntryQuality(DynamicFlattenedIndicators flattenedIndicators, 
                                                                        String strategyType, 
                                                                        ScalpingEntryLogic.EntryQuality entryQuality) {
        // Volume quality
        Double volumeMultiplier = flattenedIndicators.getNumericIndicator("volume_5min_multiplier");
        double volumeScore = volumeMultiplier != null && volumeMultiplier >= entryQuality.getMinVolumeSurge() ? 1.0 : 0.5;
        
        // RSI quality
        Double rsiValue = flattenedIndicators.getNumericIndicator("rsi_5min_value");
        double rsiScore = 0.5;
        if (rsiValue != null) {
            if ("CALL".equals(strategyType) && rsiValue >= entryQuality.getMinRsiStrength()) {
                rsiScore = 1.0;
            } else if ("PUT".equals(strategyType) && rsiValue <= entryQuality.getMinRsiStrength()) {
                rsiScore = 1.0;
            }
        }
        
        // Trend alignment
        double trendScore = calculateTrendAlignmentScore(flattenedIndicators, strategyType);
        
        // Signal strength
        double signalScore = calculateSignalStrengthScore(flattenedIndicators, strategyType);
        
        // Overall score
        double overallScore = (volumeScore + rsiScore + trendScore + signalScore) / 4.0;
        
        // Determine grade
        String grade = determineQualityGrade(overallScore);
        String description = getQualityDescription(grade);
        
        return ScalpingEntryDecision.EntryQualityScore.builder()
                .volumeScore(volumeScore)
                .momentumScore(rsiScore)
                .trendScore(trendScore)
                .signalScore(signalScore)
                .overallScore(overallScore)
                .qualityGrade(grade)
                .qualityDescription(description)
                .build();
    }

    private double calculateTrendAlignmentScore(DynamicFlattenedIndicators flattenedIndicators, String strategyType) {
        int alignedTimeframes = 0;
        List<String> timeframes = Arrays.asList("1min", "5min", "15min");
        
        for (String timeframe : timeframes) {
            String conditionKey = strategyType.equals("CALL") ? 
                "ema_" + timeframe + "_crossover" : 
                "ema_" + timeframe + "_crossdown";
            
            Boolean conditionMet = flattenedIndicators.getBooleanIndicator(conditionKey);
            if (conditionMet != null && conditionMet) {
                alignedTimeframes++;
            }
        }
        
        return (double) alignedTimeframes / timeframes.size();
    }

    private double calculateSignalStrengthScore(DynamicFlattenedIndicators flattenedIndicators, String strategyType) {
        // Count strong signals
        int strongSignals = 0;
        int totalSignals = 0;
        
        List<String> timeframes = Arrays.asList("1min", "5min", "15min");
        
        for (String timeframe : timeframes) {
            // Volume surge
            Boolean volumeSurge = flattenedIndicators.getBooleanIndicator("volume_" + timeframe + "_surge");
            if (volumeSurge != null && volumeSurge) {
                strongSignals++;
            }
            totalSignals++;
            
            // Price action
            String priceCondition = strategyType.equals("CALL") ? 
                "price_" + timeframe + "_gt_vwap" : 
                "price_" + timeframe + "_lt_vwap";
            Boolean priceAction = flattenedIndicators.getBooleanIndicator(priceCondition);
            if (priceAction != null && priceAction) {
                strongSignals++;
            }
            totalSignals++;
        }
        
        return totalSignals > 0 ? (double) strongSignals / totalSignals : 0.0;
    }

    private String determineQualityGrade(double score) {
        if (score >= 0.9) return "A";
        if (score >= 0.8) return "B";
        if (score >= 0.7) return "C";
        if (score >= 0.6) return "D";
        return "F";
    }

    private String getQualityDescription(String grade) {
        switch (grade) {
            case "A": return "Excellent entry conditions - High probability trade";
            case "B": return "Good entry conditions - Above average probability";
            case "C": return "Average entry conditions - Moderate probability";
            case "D": return "Below average conditions - Lower probability";
            case "F": return "Poor conditions - Avoid entry";
            default: return "Unknown quality grade";
        }
    }

    private ScalpingEntryDecision createRejectionDecision(String strategyType, String reason, 
                                                        double currentPrice, LocalDateTime currentTime) {
        return ScalpingEntryDecision.builder()
                .strategyType(strategyType)
                .shouldEnter(false)
                .entryPrice(currentPrice)
                .rejectionReason(reason)
                .entryTime(currentTime)
                .confidenceScore(0.0)
                .build();
    }

    private String generateCallEntryReason(List<String> satisfiedMandatory, List<String> satisfiedOptional, 
                                         double confidenceScore, ScalpingEntryDecision.EntryQualityScore qualityScore) {
        return String.format("CALL entry approved. Confidence: %.2f, Quality: %s. " +
                           "Mandatory conditions: %d/8, Optional: %d/6. " +
                           "Key signals: %s", 
                           confidenceScore, qualityScore.getQualityGrade(),
                           satisfiedMandatory.size(), satisfiedOptional.size(),
                           String.join(", ", satisfiedMandatory.subList(0, Math.min(3, satisfiedMandatory.size()))));
    }

    private String generatePutEntryReason(List<String> satisfiedMandatory, List<String> satisfiedOptional, 
                                        double confidenceScore, ScalpingEntryDecision.EntryQualityScore qualityScore) {
        return String.format("PUT entry approved. Confidence: %.2f, Quality: %s. " +
                           "Mandatory conditions: %d/8, Optional: %d/6. " +
                           "Key signals: %s", 
                           confidenceScore, qualityScore.getQualityGrade(),
                           satisfiedMandatory.size(), satisfiedOptional.size(),
                           String.join(", ", satisfiedMandatory.subList(0, Math.min(3, satisfiedMandatory.size()))));
    }

    private boolean isInTimeSlot(LocalTime time, String timeSlot) {
        try {
            String[] parts = timeSlot.split("-");
            LocalTime start = LocalTime.parse(parts[0], DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(parts[1], DateTimeFormatter.ofPattern("HH:mm"));
            return !time.isBefore(start) && !time.isAfter(end);
        } catch (Exception e) {
            log.warn("Error parsing time slot: {}", timeSlot, e);
            return false;
        }
    }

    private String determineMarketSession(LocalTime time) {
        if (time.isAfter(LocalTime.of(9, 15)) && time.isBefore(LocalTime.of(10, 0))) {
            return "OPENING";
        } else if (time.isAfter(LocalTime.of(15, 0)) && time.isBefore(LocalTime.of(15, 30))) {
            return "CLOSING";
        } else {
            return "MID";
        }
    }
}
