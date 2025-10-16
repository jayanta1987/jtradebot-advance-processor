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
        
        log.debug("Found scenario with ID: {} for field updates", existing.getId());
        
        // Build update map with proper type conversions
        Map<String, Object> updateMap = new java.util.HashMap<>();
        
        for (Map.Entry<String, Object> entry : fieldUpdates.entrySet()) {
            String fieldName = entry.getKey();
            Object newValue = entry.getValue();
            
            switch (fieldName) {
                case "description":
                    updateMap.put("description", newValue);
                    break;
                case "targetMode":
                    updateMap.put("targetMode", newValue);
                    break;
                case "minQualityScore":
                    updateMap.put("minQualityScore", convertToDouble(newValue));
                    break;
                case "flatMarketFilter":
                    updateMap.put("flatMarketFilter", newValue);
                    break;
                case "minEmaPer":
                    updateMap.put("minEmaPer", convertToDouble(newValue));
                    break;
                case "minFutureSignalPer":
                    updateMap.put("minFutureSignalPer", convertToDouble(newValue));
                    break;
                case "minCandlestickPer":
                    updateMap.put("minCandlestickPer", convertToDouble(newValue));
                    break;
                case "minMomentumPer":
                    updateMap.put("minMomentumPer", convertToDouble(newValue));
                    break;
                case "maxNTP":
                    updateMap.put("maxNTP", convertToInteger(newValue));
                    break;
                case "comments":
                    updateMap.put("comments", newValue);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown field name: " + fieldName);
            }
        }
        
        // Add updatedAt timestamp
        updateMap.put("updatedAt", LocalDateTime.now());
        
        // Use direct MongoDB update
        tradingScenarioRepository.updateScenarioFields(name, updateMap);
        
        // Fetch the updated scenario to return
        TradingScenario updated = tradingScenarioRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Scenario disappeared after update"));
        
        log.info("Updated {} fields in trading scenario: {} (ID: {})", fieldUpdates.size(), updated.getName(), updated.getId());
        return updated;
    }
    
    public void deleteScenario(String name) {
        if (!tradingScenarioRepository.existsByName(name)) {
            throw new IllegalArgumentException("Scenario with name '" + name + "' not found");
        }
        
        tradingScenarioRepository.deleteByName(name);
        log.info("Deleted trading scenario: {}", name);
    }
    
    public TradingScenario activateScenario(String name) {
        TradingScenario scenario = tradingScenarioRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Scenario with name '" + name + "' not found"));
        
        log.debug("Found scenario with ID: {} for activation", scenario.getId());
        
        LocalDateTime now = LocalDateTime.now();
        tradingScenarioRepository.updateActiveStatus(name, true, now);
        
        // Fetch the updated scenario to return
        TradingScenario updated = tradingScenarioRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Scenario disappeared after activation"));
        
        log.info("Activated trading scenario: {} (ID: {})", updated.getName(), updated.getId());
        return updated;
    }
    
    public TradingScenario deactivateScenario(String name) {
        TradingScenario scenario = tradingScenarioRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Scenario with name '" + name + "' not found"));
        
        log.debug("Found scenario with ID: {} for deactivation", scenario.getId());
        
        LocalDateTime now = LocalDateTime.now();
        tradingScenarioRepository.updateActiveStatus(name, false, now);
        
        // Fetch the updated scenario to return
        TradingScenario updated = tradingScenarioRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Scenario disappeared after deactivation"));
        
        log.info("Deactivated trading scenario: {} (ID: {})", updated.getName(), updated.getId());
        return updated;
    }
    
    /**
     * Safely converts various numeric types to Double
     * Handles Integer, Long, Float, Double, and String representations of numbers
     */
    private Double convertToDouble(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Double) {
            return (Double) value;
        }
        
        if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        }
        
        if (value instanceof Long) {
            return ((Long) value).doubleValue();
        }
        
        if (value instanceof Float) {
            return ((Float) value).doubleValue();
        }
        
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid numeric value: " + value);
            }
        }
        
        throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to Double: " + value);
    }
    
    /**
     * Safely converts various numeric types to Integer
     * Handles Integer, Long, Float, Double, and String representations of numbers
     */
    private Integer convertToInteger(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof Integer) {
            return (Integer) value;
        }
        
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        
        if (value instanceof Float) {
            return ((Float) value).intValue();
        }
        
        if (value instanceof Double) {
            return ((Double) value).intValue();
        }
        
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer value: " + value);
            }
        }
        
        throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to Integer: " + value);
    }
}
