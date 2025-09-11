package com.jtradebot.processor.service.config;

import com.jtradebot.processor.repository.NoTradeZoneFilterRepository;
import com.jtradebot.processor.repository.document.NoTradeZoneFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoTradeZoneFilterService {
    
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
    
    public NoTradeZoneFilter updateFilter(String filterName, NoTradeZoneFilter updatedFilter) {
        NoTradeZoneFilter existing = noTradeZoneFilterRepository.findByFilterName(filterName)
                .orElseThrow(() -> new IllegalArgumentException("Filter with name '" + filterName + "' not found"));
        
        // Update fields
        existing.setEnabled(updatedFilter.getEnabled());
        existing.setNtp(updatedFilter.getNtp());
        existing.setName(updatedFilter.getName());
        existing.setDescription(updatedFilter.getDescription());
        existing.setThreshold(updatedFilter.getThreshold());
        existing.setMinThreshold(updatedFilter.getMinThreshold());
        existing.setMaxThreshold(updatedFilter.getMaxThreshold());
        existing.setPriority(updatedFilter.getPriority());
        existing.setOverboughtThreshold(updatedFilter.getOverboughtThreshold());
        existing.setOversoldThreshold(updatedFilter.getOversoldThreshold());
        existing.setTimeframe(updatedFilter.getTimeframe());
        existing.setLookbackBars(updatedFilter.getLookbackBars());
        existing.setMaxConsecutiveCount(updatedFilter.getMaxConsecutiveCount());
        existing.setAnalysisWindow(updatedFilter.getAnalysisWindow());
        existing.setStartHour(updatedFilter.getStartHour());
        existing.setStartMinute(updatedFilter.getStartMinute());
        existing.setEndHour(updatedFilter.getEndHour());
        existing.setEndMinute(updatedFilter.getEndMinute());
        existing.setSupportResistanceBuffer(updatedFilter.getSupportResistanceBuffer());
        existing.setRoundFigureBuffer(updatedFilter.getRoundFigureBuffer());
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setUpdatedBy(updatedFilter.getUpdatedBy());
        existing.setVersion(existing.getVersion() + 1);
        existing.setComments(updatedFilter.getComments());
        
        NoTradeZoneFilter saved = noTradeZoneFilterRepository.save(existing);
        log.info("Updated no-trade zone filter: {}", saved.getFilterName());
        return saved;
    }
    
    public NoTradeZoneFilter updateFilterValue(String filterName, String fieldName, Object newValue) {
        NoTradeZoneFilter existing = noTradeZoneFilterRepository.findByFilterName(filterName)
                .orElseThrow(() -> new IllegalArgumentException("Filter with name '" + filterName + "' not found"));
        
        // Update specific field based on field name
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
        
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setVersion(existing.getVersion() + 1);
        
        NoTradeZoneFilter saved = noTradeZoneFilterRepository.save(existing);
        log.info("Updated filter '{}' field '{}' to '{}' in no-trade zone filter: {}", 
                filterName, fieldName, newValue, saved.getFilterName());
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
