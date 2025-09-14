package com.jtradebot.processor.service.config;

import com.jtradebot.processor.model.strategy.ScalpingEntryConfig;
import com.jtradebot.processor.repository.CategoryScoringRepository;
import com.jtradebot.processor.repository.NoTradeZoneFilterRepository;
import com.jtradebot.processor.repository.TradingScenarioRepository;
import com.jtradebot.processor.repository.document.CategoryScoring;
import com.jtradebot.processor.repository.document.NoTradeZoneFilter;
import com.jtradebot.processor.repository.document.TradingScenario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MongoConfigurationService {
    
    private final TradingScenarioRepository tradingScenarioRepository;
    private final CategoryScoringRepository categoryScoringRepository;
    private final NoTradeZoneFilterRepository noTradeZoneFilterRepository;
    
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
