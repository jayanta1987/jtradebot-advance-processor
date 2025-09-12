package com.jtradebot.processor.service.config;

import com.jtradebot.processor.repository.TradingScenarioRepository;
import com.jtradebot.processor.repository.document.TradingScenario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigTradingScenarioService {
    
    private final TradingScenarioRepository tradingScenarioRepository;
    
    public List<TradingScenario> getAllScenarios() {
        return tradingScenarioRepository.findByActiveTrue();
    }
    
    public Optional<TradingScenario> getScenarioByName(String name) {
        return tradingScenarioRepository.findByName(name);
    }
    
    public TradingScenario createScenario(TradingScenario scenario) {
        if (tradingScenarioRepository.existsByName(scenario.getName())) {
            throw new IllegalArgumentException("Scenario with name '" + scenario.getName() + "' already exists");
        }
        
        scenario.setCreatedAt(LocalDateTime.now());
        scenario.setUpdatedAt(LocalDateTime.now());
        scenario.setActive(true);
        scenario.setVersion(1);
        
        TradingScenario saved = tradingScenarioRepository.save(scenario);
        log.info("Created new trading scenario: {}", saved.getName());
        return saved;
    }
    
    public TradingScenario updateMultipleScenarioFields(String name, Map<String, Object> fieldUpdates) {
        TradingScenario existing = tradingScenarioRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Scenario with name '" + name + "' not found"));
        
        // Update multiple fields based on field names
        for (Map.Entry<String, Object> entry : fieldUpdates.entrySet()) {
            String fieldName = entry.getKey();
            Object newValue = entry.getValue();
            
            switch (fieldName) {
                case "description":
                    existing.setDescription((String) newValue);
                    break;
                case "targetMode":
                    existing.setTargetMode((String) newValue);
                    break;
                case "minQualityScore":
                    existing.setMinQualityScore((Double) newValue);
                    break;
                case "flatMarketFilter":
                    existing.setFlatMarketFilter((Boolean) newValue);
                    break;
                case "minEmaPer":
                    existing.setMinEmaPer((Double) newValue);
                    break;
                case "minFutureSignalPer":
                    existing.setMinFutureSignalPer((Double) newValue);
                    break;
                case "minCandlestickPer":
                    existing.setMinCandlestickPer((Double) newValue);
                    break;
                case "minMomentumPer":
                    existing.setMinMomentumPer((Double) newValue);
                    break;
                case "comments":
                    existing.setComments((String) newValue);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown field name: " + fieldName);
            }
        }
        
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setVersion(existing.getVersion() + 1);
        
        TradingScenario saved = tradingScenarioRepository.save(existing);
        log.info("Updated {} fields in trading scenario: {}", fieldUpdates.size(), saved.getName());
        return saved;
    }
    
    public void deleteScenario(String name) {
        if (!tradingScenarioRepository.existsByName(name)) {
            throw new IllegalArgumentException("Scenario with name '" + name + "' not found");
        }
        
        tradingScenarioRepository.deleteByName(name);
        log.info("Deleted trading scenario: {}", name);
    }
    
    public TradingScenario deactivateScenario(String name) {
        TradingScenario scenario = tradingScenarioRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Scenario with name '" + name + "' not found"));
        
        scenario.setActive(false);
        scenario.setUpdatedAt(LocalDateTime.now());
        
        TradingScenario saved = tradingScenarioRepository.save(scenario);
        log.info("Deactivated trading scenario: {}", saved.getName());
        return saved;
    }
}
