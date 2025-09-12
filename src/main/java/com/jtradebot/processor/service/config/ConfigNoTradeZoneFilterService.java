package com.jtradebot.processor.service.config;

import com.jtradebot.processor.repository.NoTradeZoneFilterRepository;
import com.jtradebot.processor.repository.document.NoTradeZoneFilter;
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
public class ConfigNoTradeZoneFilterService {
    
    private final NoTradeZoneFilterRepository noTradeZoneFilterRepository;
    
    public List<NoTradeZoneFilter> getAllFilters() {
        return noTradeZoneFilterRepository.findByActiveTrue();
    }
    
    public List<NoTradeZoneFilter> getEnabledFilters() {
        return noTradeZoneFilterRepository.findByEnabledTrue();
    }
    
    public Optional<NoTradeZoneFilter> getFilterByName(String filterName) {
        return noTradeZoneFilterRepository.findByFilterName(filterName);
    }
    
    public NoTradeZoneFilter createFilter(NoTradeZoneFilter filter) {
        if (noTradeZoneFilterRepository.existsByFilterName(filter.getFilterName())) {
            throw new IllegalArgumentException("Filter with name '" + filter.getFilterName() + "' already exists");
        }
        
        filter.setCreatedAt(LocalDateTime.now());
        filter.setUpdatedAt(LocalDateTime.now());
        filter.setActive(true);
        filter.setVersion(1);
        
        NoTradeZoneFilter saved = noTradeZoneFilterRepository.save(filter);
        log.info("Created new no-trade zone filter: {}", saved.getFilterName());
        return saved;
    }
    
    public NoTradeZoneFilter updateMultipleFilterValues(String filterName, Map<String, Object> fieldUpdates) {
        NoTradeZoneFilter existing = noTradeZoneFilterRepository.findByFilterName(filterName)
                .orElseThrow(() -> new IllegalArgumentException("Filter with name '" + filterName + "' not found"));
        
        // Update multiple fields based on field names
        for (Map.Entry<String, Object> entry : fieldUpdates.entrySet()) {
            String fieldName = entry.getKey();
            Object newValue = entry.getValue();
            
            switch (fieldName) {
                case "enabled":
                    existing.setEnabled((Boolean) newValue);
                    break;
                case "ntp":
                    existing.setNtp((Double) newValue);
                    break;
                case "threshold":
                    existing.setThreshold((Double) newValue);
                    break;
                case "minThreshold":
                    existing.setMinThreshold((Double) newValue);
                    break;
                case "maxThreshold":
                    existing.setMaxThreshold((Double) newValue);
                    break;
                case "priority":
                    existing.setPriority((Integer) newValue);
                    break;
                case "overboughtThreshold":
                    existing.setOverboughtThreshold((Double) newValue);
                    break;
                case "oversoldThreshold":
                    existing.setOversoldThreshold((Double) newValue);
                    break;
                case "maxConsecutiveCount":
                    existing.setMaxConsecutiveCount((Integer) newValue);
                    break;
                case "analysisWindow":
                    existing.setAnalysisWindow((Integer) newValue);
                    break;
                case "startHour":
                    existing.setStartHour((Integer) newValue);
                    break;
                case "startMinute":
                    existing.setStartMinute((Integer) newValue);
                    break;
                case "endHour":
                    existing.setEndHour((Integer) newValue);
                    break;
                case "endMinute":
                    existing.setEndMinute((Integer) newValue);
                    break;
                case "supportResistanceBuffer":
                    existing.setSupportResistanceBuffer((Integer) newValue);
                    break;
                case "roundFigureBuffer":
                    existing.setRoundFigureBuffer((Integer) newValue);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown field name: " + fieldName);
            }
        }
        
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setVersion(existing.getVersion() + 1);
        
        NoTradeZoneFilter saved = noTradeZoneFilterRepository.save(existing);
        log.info("Updated {} fields in no-trade zone filter: {}", fieldUpdates.size(), saved.getFilterName());
        return saved;
    }
    
    public void deleteFilter(String filterName) {
        if (!noTradeZoneFilterRepository.existsByFilterName(filterName)) {
            throw new IllegalArgumentException("Filter with name '" + filterName + "' not found");
        }
        
        noTradeZoneFilterRepository.deleteByFilterName(filterName);
        log.info("Deleted no-trade zone filter: {}", filterName);
    }
    
    public NoTradeZoneFilter deactivateFilter(String filterName) {
        NoTradeZoneFilter filter = noTradeZoneFilterRepository.findByFilterName(filterName)
                .orElseThrow(() -> new IllegalArgumentException("Filter with name '" + filterName + "' not found"));
        
        filter.setActive(false);
        filter.setUpdatedAt(LocalDateTime.now());
        
        NoTradeZoneFilter saved = noTradeZoneFilterRepository.save(filter);
        log.info("Deactivated no-trade zone filter: {}", saved.getFilterName());
        return saved;
    }
}
