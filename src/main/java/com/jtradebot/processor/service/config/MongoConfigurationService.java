package com.jtradebot.processor.service.config;

import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.repository.CategoryScoringRepository;
import com.jtradebot.processor.repository.NoTradeZoneFilterRepository;
import com.jtradebot.processor.repository.RiskManagementSettingRepository;
import com.jtradebot.processor.repository.TradingScenarioRepository;
import com.jtradebot.processor.repository.document.CategoryScoring;
import com.jtradebot.processor.repository.document.NoTradeZoneFilter;
import com.jtradebot.processor.repository.document.RiskManagementSetting;
import com.jtradebot.processor.repository.document.TradingScenario;
import com.jtradebot.processor.config.TradingConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MongoConfigurationService {
    
    private final TradingScenarioRepository tradingScenarioRepository;
    private final CategoryScoringRepository categoryScoringRepository;
    private final NoTradeZoneFilterRepository noTradeZoneFilterRepository;
    private final RiskManagementSettingRepository riskManagementSettingRepository;
    
    public List<ScalpingEntryConfig.Scenario> getScenariosFromMongoDB() {
        List<TradingScenario> mongoScenarios = tradingScenarioRepository.findByActiveTrue();
        
        return mongoScenarios.stream()
                .map(this::convertToScalpingEntryScenario)
                .collect(Collectors.toList());
    }
    
    public ScalpingEntryConfig.CategoryScoring getCategoryScoringFromMongoDB() {
        List<CategoryScoring> mongoCategoryScorings = categoryScoringRepository.findByActiveTrue();
        
        Map<String, ScalpingEntryConfig.CategoryIndicatorScoring> callCategories = new HashMap<>();
        Map<String, ScalpingEntryConfig.CategoryIndicatorScoring> putCategories = new HashMap<>();
        
        for (CategoryScoring categoryScoring : mongoCategoryScorings) {
            ScalpingEntryConfig.CategoryIndicatorScoring indicatorScoring = 
                    ScalpingEntryConfig.CategoryIndicatorScoring.builder()
                            .indicators(categoryScoring.getIndicators())
                            .build();
            
            if ("callCategories".equals(categoryScoring.getCategoryType())) {
                callCategories.put(categoryScoring.getCategoryName(), indicatorScoring);
            } else if ("putCategories".equals(categoryScoring.getCategoryType())) {
                putCategories.put(categoryScoring.getCategoryName(), indicatorScoring);
            }
        }
        
        return ScalpingEntryConfig.CategoryScoring.builder()
                .callCategories(callCategories)
                .putCategories(putCategories)
                .build();
    }
    
    public ScalpingEntryConfig.NoTradeZonesConfig getNoTradeZonesFromMongoDB() {
        List<NoTradeZoneFilter> mongoFilters = noTradeZoneFilterRepository.findByActiveTrue();
        
        Map<String, ScalpingEntryConfig.NoTradeFilter> filters = new HashMap<>();
        
        for (NoTradeZoneFilter filter : mongoFilters) {
            ScalpingEntryConfig.NoTradeFilter noTradeFilter = ScalpingEntryConfig.NoTradeFilter.builder()
                    .enabled(filter.getEnabled())
                    .ntp(filter.getNtp())
                    .name(filter.getName())
                    .description(filter.getDescription())
                    .threshold(filter.getThreshold())
                    .minThreshold(filter.getMinThreshold())
                    .maxThreshold(filter.getMaxThreshold())
                    .priority(filter.getPriority())
                    .overboughtThreshold(filter.getOverboughtThreshold())
                    .oversoldThreshold(filter.getOversoldThreshold())
                    .timeframe(filter.getTimeframe())
                    .lookbackBars(filter.getLookbackBars())
                    .maxConsecutiveCount(filter.getMaxConsecutiveCount())
                    .analysisWindow(filter.getAnalysisWindow())
                    .startHour(filter.getStartHour())
                    .startMinute(filter.getStartMinute())
                    .endHour(filter.getEndHour())
                    .endMinute(filter.getEndMinute())
                    .supportResistanceBuffer(filter.getSupportResistanceBuffer())
                    .roundFigureBuffer(filter.getRoundFigureBuffer())
                    .build();
            
            filters.put(filter.getFilterName(), noTradeFilter);
        }
        
        return ScalpingEntryConfig.NoTradeZonesConfig.builder()
                .enabled(true) // Default to enabled
                .description("No Trade Zones configuration loaded from MongoDB")
                .maxAllowedNTP(2) // Default value, can be made configurable
                .filters(filters)
                .build();
    }
    
    public TradingConfigurationService.TradingConfig getRiskManagementFromMongoDB() {
        log.info("Attempting to load risk management configuration from MongoDB...");
        Optional<RiskManagementSetting> mongoSetting = riskManagementSettingRepository.findByActiveTrue();
        
        if (mongoSetting.isEmpty()) {
            log.error("❌ No active risk management setting found in MongoDB. Please ensure a RiskManagementSetting document exists with active=true");
            return null;
        }
        
        RiskManagementSetting setting = mongoSetting.get();
        log.info("✅ Found active risk management setting in MongoDB: ID={}, minMilestonePoints={}, maxMilestonePoints={}", 
                setting.getId(), setting.getMinMilestonePoints(), setting.getMaxMilestonePoints());
        
        // Build RiskManagement
        TradingConfigurationService.RsiThresholds rsiThresholds = TradingConfigurationService.RsiThresholds.builder()
                .rsiMaPeriod(setting.getRsiMaPeriod() != null ? setting.getRsiMaPeriod() : 14)
                .enableRsiMaComparison(setting.getEnableRsiMaComparison() != null ? setting.getEnableRsiMaComparison() : true)
                .build();
        
        TradingConfigurationService.RiskManagement riskManagement = TradingConfigurationService.RiskManagement.builder()
                .minMilestonePoints(setting.getMinMilestonePoints() != null ? setting.getMinMilestonePoints() : 2.0)
                .maxMilestonePoints(setting.getMaxMilestonePoints() != null ? setting.getMaxMilestonePoints() : 3.0)
                .rsiThresholds(rsiThresholds)
                .volumeSurgeMultiplierMin(setting.getVolumeSurgeMultiplierMin() != null ? setting.getVolumeSurgeMultiplierMin() : 1.5)
                .stopLossPercentage(setting.getStopLossPercentage() != null ? setting.getStopLossPercentage() : 2.0)
                .targetPercentage(setting.getTargetPercentage() != null ? setting.getTargetPercentage() : 5.0)
                .build();
        
        // Build ExitSignalConfiguration
        TradingConfigurationService.ExitThresholds exitThresholds = TradingConfigurationService.ExitThresholds.builder()
                .callExitThreshold(setting.getCallExitThreshold() != null ? setting.getCallExitThreshold() : 0.5)
                .putExitThreshold(setting.getPutExitThreshold() != null ? setting.getPutExitThreshold() : 0.5)
                .description("Exit thresholds loaded from MongoDB")
                .build();
        
        TradingConfigurationService.RsiDivergenceExit rsiDivergenceExit = TradingConfigurationService.RsiDivergenceExit.builder()
                .enabled(setting.getRsiDivergenceExitEnabled() != null ? setting.getRsiDivergenceExitEnabled() : true)
                .description("RSI divergence exit configuration")
                .build();
        
        TradingConfigurationService.MarketConditionExit marketConditionExit = TradingConfigurationService.MarketConditionExit.builder()
                .enabled(setting.getMarketConditionExitEnabled() != null ? setting.getMarketConditionExitEnabled() : true)
                .description("Market condition exit configuration")
                .build();
        
        TradingConfigurationService.ExitSignalConfiguration exitSignalConfiguration = TradingConfigurationService.ExitSignalConfiguration.builder()
                .enabled(true)
                .description("Exit signal configuration loaded from MongoDB")
                .exitThresholds(exitThresholds)
                .rsiDivergenceExit(rsiDivergenceExit)
                .marketConditionExit(marketConditionExit)
                .build();
        
        // Build and return TradingConfig
        TradingConfigurationService.TradingConfig tradingConfig = TradingConfigurationService.TradingConfig.builder()
                .riskManagement(riskManagement)
                .exitSignalConfiguration(exitSignalConfiguration)
                .build();
        
        log.info("✅ Successfully built TradingConfig from MongoDB: riskManagement={}, exitSignalConfiguration={}", 
                riskManagement != null ? "LOADED" : "NULL", exitSignalConfiguration != null ? "LOADED" : "NULL");
        
        return tradingConfig;
    }
    
    public boolean isMarketEndSchedulerEnabledFromMongoDB() {
        Optional<RiskManagementSetting> mongoSetting = riskManagementSettingRepository.findByActiveTrue();
        return mongoSetting.map(RiskManagementSetting::getMarketEndSchedulerEnabled)
                .orElse(true); // Default to enabled
    }
    
    private ScalpingEntryConfig.Scenario convertToScalpingEntryScenario(TradingScenario mongoScenario) {
        ScalpingEntryConfig.ScenarioRequirements requirements = ScalpingEntryConfig.ScenarioRequirements.builder()
                .minQualityScore(mongoScenario.getMinQualityScore())
                .flatMarketFilter(mongoScenario.getFlatMarketFilter())
                .min_ema_per(mongoScenario.getMinEmaPer())
                .min_future_signal_per(mongoScenario.getMinFutureSignalPer())
                .min_candlestick_per(mongoScenario.getMinCandlestickPer())
                .min_momentum_per(mongoScenario.getMinMomentumPer())
                .maxNTP(mongoScenario.getMaxNTP())
                .build();
        
        return ScalpingEntryConfig.Scenario.builder()
                .name(mongoScenario.getName())
                .description(mongoScenario.getDescription())
                .requirements(requirements)
                .targetMode(mongoScenario.getTargetMode())
                .build();
    }
}
