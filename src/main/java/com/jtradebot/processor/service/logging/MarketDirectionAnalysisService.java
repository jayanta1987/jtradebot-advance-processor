package com.jtradebot.processor.service.logging;

import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import com.jtradebot.processor.config.DynamicStrategyConfigService;
import com.jtradebot.processor.model.strategy.FlatMarketFilteringConfig;
import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.service.analysis.CategoryAnalysisService;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDirectionAnalysisService {

    private final DynamicStrategyConfigService configService;
    private final CategoryAnalysisService categoryAnalysisService;


    public String getTrendAndConditionsInfoForLog(ScalpingEntryDecision entryDecision, FlattenedIndicators indicators, Tick tick, boolean isMarketSuitable, EntryQuality callQuality, EntryQuality putQuality) {
        try {
            // Use scenario information from entry decision
            if (entryDecision.isShouldEntry() && entryDecision.getScenarioName() != null) {
                String scenarioInfo = String.format("Scenario: %s (%.1f/10)", 
                    entryDecision.getScenarioName(), entryDecision.getQualityScore());
                
                if (entryDecision.getCategoryScores() != null) {
                    // Get scenario requirements to check if categories pass
                    Map<String, Integer> categoryScores = entryDecision.getCategoryScores();
                    
                    // Get scenario requirements from config
                    List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
                    ScalpingEntryConfig.ScenarioRequirements requirements = null;
                    
                    for (ScalpingEntryConfig.Scenario scenario : scenarios) {
                        if (scenario.getName().equals(entryDecision.getScenarioName())) {
                            requirements = scenario.getRequirements();
                            break;
                        }
                    }
                    
                    // Check if flat market filtering would adjust these requirements
                    boolean requirementsAdjusted = false;
                    int categoryIncrement = 0;
                    String flatMarketReason = "";
                    if (requirements != null && requirements.getFlatMarketFilter() != null && requirements.getFlatMarketFilter()) {
                        // Use the passed market condition analysis instead of recalculating
                        if (!isMarketSuitable) {
                            requirementsAdjusted = true;
                            // Get the actual category increment from configuration
                            FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
                            categoryIncrement = config.getThresholds().getFlatMarketAdjustments().getCategoryIncrement();
                        }
                    }
                    
                    // Build category info with green ticks
                    StringBuilder categoryInfo = new StringBuilder();
                    
                    // Quality Score
                    if (requirements != null && requirements.getMinQualityScore() != null) {
                        boolean qsPass = entryDecision.getQualityScore() >= requirements.getMinQualityScore();
                        categoryInfo.append(String.format("QS:%.1f/%.1f%s ", 
                            entryDecision.getQualityScore(), requirements.getMinQualityScore(), qsPass ? "✅" : ""));
                    }
                    
                    // EMA
                    int emaCount = categoryScores.getOrDefault("ema", 0);
                    boolean emaPass = requirements != null && requirements.getEma_min_count() != null && 
                                   emaCount >= requirements.getEma_min_count();
                    String emaAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    categoryInfo.append(String.format("EMA:%d/%d%s%s ", 
                        emaCount, requirements != null ? requirements.getEma_min_count() : 0, emaPass ? "✅" : "", emaAdjustment));
                    
                    // FutureAndVolume
                    int fvCount = categoryScores.getOrDefault("futureAndVolume", 0);
                    boolean fvPass = requirements != null && requirements.getFutureAndVolume_min_count() != null && 
                                   fvCount >= requirements.getFutureAndVolume_min_count();
                    String fvAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    categoryInfo.append(String.format("FV:%d/%d%s%s ", 
                        fvCount, requirements != null ? requirements.getFutureAndVolume_min_count() : 0, fvPass ? "✅" : "", fvAdjustment));
                    
                    // Candlestick
                    int csCount = categoryScores.getOrDefault("candlestick", 0);
                    boolean csPass = requirements != null && requirements.getCandlestick_min_count() != null && 
                                   csCount >= requirements.getCandlestick_min_count();
                    String csAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    categoryInfo.append(String.format("CS:%d/%d%s%s ", 
                        csCount, requirements != null ? requirements.getCandlestick_min_count() : 0, csPass ? "✅" : "", csAdjustment));
                    
                    // Momentum
                    int mCount = categoryScores.getOrDefault("momentum", 0);
                    boolean mPass = requirements != null && requirements.getMomentum_min_count() != null && 
                                   mCount >= requirements.getMomentum_min_count();
                    String mAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    categoryInfo.append(String.format("M:%d/%d%s%s ", 
                        mCount, requirements != null ? requirements.getMomentum_min_count() : 0, mPass ? "✅" : "", mAdjustment));
                    
                    return String.format("🎯 %s | %s%s", scenarioInfo, categoryInfo.toString().trim(), flatMarketReason);
                } else {
                    return String.format("🎯 %s", scenarioInfo);
                }
            } else {
                // Show scenario-based evaluation when no scenario is triggered
                String evaluation = getScenarioBasedEvaluation(indicators, tick, isMarketSuitable, callQuality, putQuality);
                // Add market direction info to non-entry logs as well
                return evaluation;
            }
            
        } catch (Exception e) {
            return "🎯 ERROR";
        }
    }

    /**
     * Get scenario-based evaluation showing which scenarios are being checked and their requirements
     */
    private String getScenarioBasedEvaluation(FlattenedIndicators indicators, Tick tick, boolean isMarketSuitable, EntryQuality callQuality, EntryQuality putQuality) {
        try {
            // 🔥 OPTIMIZATION: Use passed quality scores instead of recalculating
            String dominantTrend = callQuality.getQualityScore() > putQuality.getQualityScore() ? "CALL" : "PUT";
            double dominantQuality = Math.max(callQuality.getQualityScore(), putQuality.getQualityScore());
            
            // Get scenarios from configuration
            List<ScalpingEntryConfig.Scenario> scenarios = configService.getScenarios();
            
            // Find the best scenario that could pass
            String bestScenarioInfo = "";
            for (ScalpingEntryConfig.Scenario scenario : scenarios) {
                ScalpingEntryConfig.ScenarioRequirements requirements = scenario.getRequirements();
                
                // Check if flat market filtering would adjust these requirements
                boolean requirementsAdjusted = false;
                int categoryIncrement = 0;
                String flatMarketReason = "";
                if (requirements != null && requirements.getFlatMarketFilter() != null && requirements.getFlatMarketFilter()) {
                    // Use the passed market condition analysis instead of recalculating
                    if (!isMarketSuitable) {
                        requirementsAdjusted = true;
                        // Get the actual category increment from configuration
                        FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
                        categoryIncrement = config.getThresholds().getFlatMarketAdjustments().getCategoryIncrement();
                    }
                }
                
                // Get category counts for the dominant trend
                Map<String, Integer> categoryCounts = categoryAnalysisService.getCategoryCountsMap(indicators, dominantTrend);
                
                // Check if this scenario could pass
                boolean couldPass = true;
                StringBuilder scenarioDetails = new StringBuilder();
                
                // Check quality score requirement
                if (requirements.getMinQualityScore() != null) {
                    boolean qualityPass = dominantQuality >= requirements.getMinQualityScore();
                    scenarioDetails.append(String.format("QS:%.1f/%.1f%s ", 
                        dominantQuality, requirements.getMinQualityScore(), qualityPass ? "✅" : ""));
                    if (!qualityPass) couldPass = false;
                }
                
                // Check category requirements
                if (requirements.getEma_min_count() != null) {
                    int emaCount = categoryCounts.getOrDefault("ema", 0);
                    boolean emaPass = emaCount >= requirements.getEma_min_count();
                    String emaAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    scenarioDetails.append(String.format("EMA:%d/%d%s%s ", 
                        emaCount, requirements.getEma_min_count(), emaPass ? "✅" : "", emaAdjustment));
                    if (!emaPass) couldPass = false;
                }
                
                if (requirements.getFutureAndVolume_min_count() != null) {
                    int fvCount = categoryCounts.getOrDefault("futureAndVolume", 0);
                    boolean fvPass = fvCount >= requirements.getFutureAndVolume_min_count();
                    String fvAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    scenarioDetails.append(String.format("FV:%d/%d%s%s ", 
                        fvCount, requirements.getFutureAndVolume_min_count(), fvPass ? "✅" : "", fvAdjustment));
                    if (!fvPass) couldPass = false;
                }
                
                if (requirements.getCandlestick_min_count() != null) {
                    int csCount = categoryCounts.getOrDefault("candlestick", 0);
                    boolean csPass = csCount >= requirements.getCandlestick_min_count();
                    String csAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    scenarioDetails.append(String.format("CS:%d/%d%s%s ", 
                        csCount, requirements.getCandlestick_min_count(), csPass ? "✅" : "", csAdjustment));
                    if (!csPass) couldPass = false;
                }
                
                if (requirements.getMomentum_min_count() != null) {
                    int mCount = categoryCounts.getOrDefault("momentum", 0);
                    boolean mPass = mCount >= requirements.getMomentum_min_count();
                    String mAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                    scenarioDetails.append(String.format("M:%d/%d%s%s ", 
                        mCount, requirements.getMomentum_min_count(), mPass ? "✅" : "", mAdjustment));
                    if (!mPass) couldPass = false;
                }
                
                // If this scenario could pass, show it
                if (couldPass) {
                    bestScenarioInfo = String.format("🎯 %s (%.1f/10) | %s: %s%s", 
                        dominantTrend, dominantQuality, scenario.getName(), scenarioDetails.toString().trim(), flatMarketReason);
                    break;
                }
            }
            
            // If no scenario could pass, show all scenarios being evaluated
            if (bestScenarioInfo.isEmpty() && !scenarios.isEmpty()) {
                StringBuilder allScenariosInfo = new StringBuilder();
                
                for (int i = 0; i < scenarios.size(); i++) {
                    ScalpingEntryConfig.Scenario scenario = scenarios.get(i);
                    ScalpingEntryConfig.ScenarioRequirements requirements = scenario.getRequirements();
                    Map<String, Integer> categoryCounts = categoryAnalysisService.getCategoryCountsMap(indicators, dominantTrend);
                    
                    // Check if flat market filtering would adjust these requirements
                    boolean requirementsAdjusted = false;
                    int categoryIncrement = 0;
                    String flatMarketReason = "";
                    if (requirements != null && requirements.getFlatMarketFilter() != null && requirements.getFlatMarketFilter()) {
                        // Use the passed market condition analysis instead of recalculating
                        if (!isMarketSuitable) {
                            requirementsAdjusted = true;
                            // Get the actual category increment from configuration
                            FlatMarketFilteringConfig config = configService.getFlatMarketFilteringConfig();
                            categoryIncrement = config.getThresholds().getFlatMarketAdjustments().getCategoryIncrement();

                        }
                    }
                    
                    StringBuilder scenarioDetails = new StringBuilder();
                    if (requirements.getMinQualityScore() != null) {
                        boolean qsPass = dominantQuality >= requirements.getMinQualityScore();
                        scenarioDetails.append(String.format("QS:%.1f/%.1f%s ", 
                            dominantQuality, requirements.getMinQualityScore(), qsPass ? "✅" : ""));
                    }
                    if (requirements.getEma_min_count() != null) {
                        int emaCount = categoryCounts.getOrDefault("ema", 0);
                        boolean emaPass = emaCount >= requirements.getEma_min_count();
                        String emaAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                        scenarioDetails.append(String.format("EMA:%d/%d%s%s ", 
                            emaCount, requirements.getEma_min_count(), emaPass ? "✅" : "", emaAdjustment));
                    }
                    if (requirements.getFutureAndVolume_min_count() != null) {
                        int fvCount = categoryCounts.getOrDefault("futureAndVolume", 0);
                        boolean fvPass = fvCount >= requirements.getFutureAndVolume_min_count();
                        String fvAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                        scenarioDetails.append(String.format("FV:%d/%d%s%s ", 
                            fvCount, requirements.getFutureAndVolume_min_count(), fvPass ? "✅" : "", fvAdjustment));
                    }
                    if (requirements.getCandlestick_min_count() != null) {
                        int csCount = categoryCounts.getOrDefault("candlestick", 0);
                        boolean csPass = csCount >= requirements.getCandlestick_min_count();
                        String csAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                        scenarioDetails.append(String.format("CS:%d/%d%s%s ", 
                            csCount, requirements.getCandlestick_min_count(), csPass ? "✅" : "", csAdjustment));
                    }
                    if (requirements.getMomentum_min_count() != null) {
                        int mCount = categoryCounts.getOrDefault("momentum", 0);
                        boolean mPass = mCount >= requirements.getMomentum_min_count();
                        String mAdjustment = requirementsAdjusted ? " (+" + categoryIncrement + ")" : "";
                        scenarioDetails.append(String.format("M:%d/%d%s%s ", 
                            mCount, requirements.getMomentum_min_count(), mPass ? "✅" : "", mAdjustment));
                    }
                    
                    if (i > 0) allScenariosInfo.append(" | ");
                    allScenariosInfo.append(String.format("%s: %s%s", 
                        scenario.getName(), scenarioDetails.toString().trim(), flatMarketReason));
                }
                
                bestScenarioInfo = String.format("🎯 %s (%.1f/10) | %s", 
                    dominantTrend, dominantQuality, allScenariosInfo.toString());
            }
            
            return bestScenarioInfo;
            
        } catch (Exception e) {
            return "🎯 ERROR";
        }
    }


}
