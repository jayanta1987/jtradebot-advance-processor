package com.jtradebot.processor.service.config;

import com.jtradebot.processor.repository.TradingScenarioRepository;
import com.jtradebot.processor.repository.document.TradingScenario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradingScenarioService {
    
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
    
    public TradingScenario updateScenario(String name, TradingScenario updatedScenario) {
        TradingScenario existing = tradingScenarioRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Scenario with name '" + name + "' not found"));
        
        // Update fields
        existing.setDescription(updatedScenario.getDescription());
        existing.setTargetMode(updatedScenario.getTargetMode());
        existing.setMinQualityScore(updatedScenario.getMinQualityScore());
        existing.setFlatMarketFilter(updatedScenario.getFlatMarketFilter());
        existing.setMinEmaPer(updatedScenario.getMinEmaPer());
        existing.setMinFutureSignalPer(updatedScenario.getMinFutureSignalPer());
        existing.setMinCandlestickPer(updatedScenario.getMinCandlestickPer());
        existing.setMinMomentumPer(updatedScenario.getMinMomentumPer());
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setUpdatedBy(updatedScenario.getUpdatedBy());
        existing.setVersion(existing.getVersion() + 1);
        existing.setComments(updatedScenario.getComments());
        
        TradingScenario saved = tradingScenarioRepository.save(existing);
        log.info("Updated trading scenario: {}", saved.getName());
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
