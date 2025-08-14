package com.jtradebot.processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.jtradebot.processor.service.BacktestDataCollectorService.BacktestDataPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestDataAnalysisService {
    
    private final ObjectMapper objectMapper;
    
    /**
     * Analyze backtest data from JSON file
     */
    public Map<String, Object> analyzeBacktestData(String jsonFileName) {
        try {
            log.info("🔍 Analyzing backtest data from: {}", jsonFileName);
            
            // Load data
            Map<String, List<BacktestDataPoint>> data = loadBacktestData(jsonFileName);
            if (data.isEmpty()) {
                return Map.of("error", "No data found in file: " + jsonFileName);
            }
            
            // Flatten all data points
            List<BacktestDataPoint> allDataPoints = data.values().stream()
                    .flatMap(List::stream)
                    .sorted(Comparator.comparing(BacktestDataPoint::getTimestamp))
                    .collect(Collectors.toList());
            
            return performAnalysis(allDataPoints);
            
        } catch (Exception e) {
            log.error("Error analyzing backtest data: {}", e.getMessage(), e);
            return Map.of("error", "Analysis failed: " + e.getMessage());
        }
    }
    
    /**
     * Analyze backtest data from directory (multiple files)
     */
    public Map<String, Object> analyzeBacktestDataFromDirectory(String directoryPath) {
        try {
            log.info("🔍 Analyzing backtest data from directory: {}", directoryPath);
            
            // Find all JSON files in directory
            List<File> jsonFiles = findJsonFiles(directoryPath);
            if (jsonFiles.isEmpty()) {
                return Map.of("error", "No JSON files found in directory: " + directoryPath);
            }
            
            log.info("📁 Found {} JSON files to analyze", jsonFiles.size());
            
            // Load and combine all data
            List<BacktestDataPoint> allDataPoints = new ArrayList<>();
            Map<String, Object> fileStats = new HashMap<>();
            
            for (File file : jsonFiles) {
                try {
                    Map<String, List<BacktestDataPoint>> fileData = loadBacktestData(file.getAbsolutePath());
                    List<BacktestDataPoint> filePoints = fileData.values().stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    
                    allDataPoints.addAll(filePoints);
                    
                    fileStats.put(file.getName(), Map.of(
                            "dataPoints", filePoints.size(),
                            "fileSize", file.length()
                    ));
                    
                    log.debug("📄 Loaded {} data points from {}", filePoints.size(), file.getName());
                    
                } catch (Exception e) {
                    log.warn("⚠️ Error loading file {}: {}", file.getName(), e.getMessage());
                    fileStats.put(file.getName(), Map.of("error", e.getMessage()));
                }
            }
            
            if (allDataPoints.isEmpty()) {
                return Map.of("error", "No valid data found in any files");
            }
            
            // Sort all data points by timestamp
            allDataPoints.sort(Comparator.comparing(BacktestDataPoint::getTimestamp));
            
            log.info("📊 Total data points loaded: {}", allDataPoints.size());
            
            // Perform analysis
            Map<String, Object> analysis = performAnalysis(allDataPoints);
            
            // Add file statistics
            analysis.put("fileStats", fileStats);
            analysis.put("filesProcessed", jsonFiles.size());
            
            return analysis;
            
        } catch (Exception e) {
            log.error("Error analyzing backtest data from directory: {}", e.getMessage(), e);
            return Map.of("error", "Analysis failed: " + e.getMessage());
        }
    }
    
    /**
     * Analyze backtest data by session ID
     */
    public Map<String, Object> analyzeBacktestDataBySession(String sessionId) {
        try {
            log.info("🔍 Analyzing backtest data for session: {}", sessionId);
            
            // Find all files for this session
            List<File> sessionFiles = findSessionFiles(sessionId);
            if (sessionFiles.isEmpty()) {
                return Map.of("error", "No files found for session: " + sessionId);
            }
            
            log.info("📁 Found {} files for session {}", sessionFiles.size(), sessionId);
            
            // Load and combine all data
            List<BacktestDataPoint> allDataPoints = new ArrayList<>();
            
            for (File file : sessionFiles) {
                try {
                    Map<String, List<BacktestDataPoint>> fileData = loadBacktestData(file.getAbsolutePath());
                    List<BacktestDataPoint> filePoints = fileData.values().stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    
                    allDataPoints.addAll(filePoints);
                    log.debug("📄 Loaded {} data points from {}", filePoints.size(), file.getName());
                    
                } catch (Exception e) {
                    log.warn("⚠️ Error loading file {}: {}", file.getName(), e.getMessage());
                }
            }
            
            if (allDataPoints.isEmpty()) {
                return Map.of("error", "No valid data found for session: " + sessionId);
            }
            
            // Sort all data points by timestamp
            allDataPoints.sort(Comparator.comparing(BacktestDataPoint::getTimestamp));
            
            log.info("📊 Total data points for session {}: {}", sessionId, allDataPoints.size());
            
            // Perform analysis
            Map<String, Object> analysis = performAnalysis(allDataPoints);
            analysis.put("sessionId", sessionId);
            analysis.put("filesProcessed", sessionFiles.size());
            
            return analysis;
            
        } catch (Exception e) {
            log.error("Error analyzing backtest data for session: {}", e.getMessage(), e);
            return Map.of("error", "Analysis failed: " + e.getMessage());
        }
    }
    
    /**
     * Perform analysis on data points
     */
    private Map<String, Object> performAnalysis(List<BacktestDataPoint> allDataPoints) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Basic statistics
        analysis.put("basicStats", calculateBasicStats(allDataPoints));
        
        // Entry analysis
        analysis.put("entryAnalysis", analyzeEntries(allDataPoints));
        
        // Trend analysis
        analysis.put("trendAnalysis", analyzeTrends(allDataPoints));
        
        // Indicator analysis
        analysis.put("indicatorAnalysis", analyzeIndicators(allDataPoints));
        
        // Quality analysis
        analysis.put("qualityAnalysis", analyzeQuality(allDataPoints));
        
        // Time-based analysis
        analysis.put("timeAnalysis", analyzeTimePatterns(allDataPoints));
        
        // Recommendations
        analysis.put("recommendations", generateRecommendations(allDataPoints));
        
        log.info("✅ Analysis completed for {} data points", allDataPoints.size());
        return analysis;
    }
    
    /**
     * Load backtest data from JSON file
     */
    private Map<String, List<BacktestDataPoint>> loadBacktestData(String fileName) {
        try {
            File file = new File(fileName);
            if (!file.exists()) {
                log.error("File not found: {}", fileName);
                return new HashMap<>();
            }
            
            // Create proper type reference for nested generic types
            JavaType stringType = objectMapper.getTypeFactory().constructType(String.class);
            JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, BacktestDataPoint.class);
            JavaType mapType = objectMapper.getTypeFactory().constructMapType(HashMap.class, stringType, listType);
            
            return objectMapper.readValue(file, mapType);
            
        } catch (Exception e) {
            log.error("Error loading backtest data: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }
    
    /**
     * Find all JSON files in directory
     */
    private List<File> findJsonFiles(String directoryPath) {
        List<File> jsonFiles = new ArrayList<>();
        try {
            File directory = new File(directoryPath);
            if (!directory.exists() || !directory.isDirectory()) {
                log.error("Directory not found: {}", directoryPath);
                return jsonFiles;
            }
            
            File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files != null) {
                jsonFiles.addAll(Arrays.asList(files));
            }
            
        } catch (Exception e) {
            log.error("Error finding JSON files in directory: {}", e.getMessage(), e);
        }
        return jsonFiles;
    }
    
    /**
     * Find all files for a specific session
     */
    private List<File> findSessionFiles(String sessionId) {
        List<File> sessionFiles = new ArrayList<>();
        try {
            // Look in the default output directory
            String outputDir = "./backtest-data";
            File directory = new File(outputDir);
            
            if (!directory.exists() || !directory.isDirectory()) {
                log.error("Output directory not found: {}", outputDir);
                return sessionFiles;
            }
            
            File[] files = directory.listFiles((dir, name) -> 
                name.toLowerCase().contains(sessionId.toLowerCase()) && 
                name.toLowerCase().endsWith(".json"));
                
            if (files != null) {
                sessionFiles.addAll(Arrays.asList(files));
            }
            
        } catch (Exception e) {
            log.error("Error finding session files: {}", e.getMessage(), e);
        }
        return sessionFiles;
    }
    
    /**
     * Calculate basic statistics
     */
    private Map<String, Object> calculateBasicStats(List<BacktestDataPoint> dataPoints) {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalDataPoints", dataPoints.size());
        stats.put("sessionId", dataPoints.get(0).getSessionId());
        stats.put("startTime", dataPoints.get(0).getTimestamp());
        stats.put("endTime", dataPoints.get(dataPoints.size() - 1).getTimestamp());
        
        // Price statistics
        DoubleSummaryStatistics priceStats = dataPoints.stream()
                .mapToDouble(BacktestDataPoint::getIndexPrice)
                .summaryStatistics();
        
        stats.put("minPrice", priceStats.getMin());
        stats.put("maxPrice", priceStats.getMax());
        stats.put("avgPrice", priceStats.getAverage());
        stats.put("priceRange", priceStats.getMax() - priceStats.getMin());
        
        // Volume statistics - Use future volume since index volume is always 0
        DoubleSummaryStatistics volumeStats = dataPoints.stream()
                .mapToDouble(dp -> dp.getFutureVolume() != null ? dp.getFutureVolume() : 0)
                .summaryStatistics();
        
        stats.put("minVolume", volumeStats.getMin());
        stats.put("maxVolume", volumeStats.getMax());
        stats.put("avgVolume", volumeStats.getAverage());
        
        return stats;
    }
    
    /**
     * Analyze entry signals
     */
    private Map<String, Object> analyzeEntries(List<BacktestDataPoint> dataPoints) {
        Map<String, Object> analysis = new HashMap<>();
        
        long totalCallSignals = dataPoints.stream()
                .filter(BacktestDataPoint::isShouldMakeCallEntry)
                .count();
        
        long totalPutSignals = dataPoints.stream()
                .filter(BacktestDataPoint::isShouldMakePutEntry)
                .count();
        
        analysis.put("totalCallSignals", totalCallSignals);
        analysis.put("totalPutSignals", totalPutSignals);
        analysis.put("totalSignals", totalCallSignals + totalPutSignals);
        analysis.put("callSignalPercentage", (double) totalCallSignals / dataPoints.size() * 100);
        analysis.put("putSignalPercentage", (double) totalPutSignals / dataPoints.size() * 100);
        
        return analysis;
    }
    
    /**
     * Analyze trends
     */
    private Map<String, Object> analyzeTrends(List<BacktestDataPoint> dataPoints) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Count trend strengths
        Map<String, Long> trendStrengthCounts = dataPoints.stream()
                .map(dp -> dp.getTrendAnalysis().getTrendStrength())
                .collect(Collectors.groupingBy(
                        strength -> strength != null ? strength : "UNKNOWN",
                        Collectors.counting()));
        
        analysis.put("trendStrengthDistribution", trendStrengthCounts);
        
        // Average signals per timeframe
        DoubleSummaryStatistics emaSignals = dataPoints.stream()
                .mapToDouble(dp -> dp.getTrendAnalysis().getEmaSignals())
                .summaryStatistics();
        
        DoubleSummaryStatistics rsiSignals = dataPoints.stream()
                .mapToDouble(dp -> dp.getTrendAnalysis().getRsiSignals())
                .summaryStatistics();
        
        DoubleSummaryStatistics volumeSignals = dataPoints.stream()
                .mapToDouble(dp -> dp.getTrendAnalysis().getVolumeSignals())
                .summaryStatistics();
        
        analysis.put("avgEmaSignals", emaSignals.getAverage());
        analysis.put("avgRsiSignals", rsiSignals.getAverage());
        analysis.put("avgVolumeSignals", volumeSignals.getAverage());
        
        return analysis;
    }
    
    /**
     * Analyze indicators
     */
    private Map<String, Object> analyzeIndicators(List<BacktestDataPoint> dataPoints) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Count indicator satisfactions
        long emaSatisfied = dataPoints.stream()
                .filter(dp -> dp.getFlattenedIndicators() != null)
                .filter(dp -> dp.getFlattenedIndicators().getEma9_5min_gt_ema21_5min() != null && 
                             dp.getFlattenedIndicators().getEma9_5min_gt_ema21_5min())
                .count();
        
        long rsiSatisfied = dataPoints.stream()
                .filter(dp -> dp.getFlattenedIndicators() != null)
                .filter(dp -> dp.getFlattenedIndicators().getRsi_5min_gt_56() != null && 
                             dp.getFlattenedIndicators().getRsi_5min_gt_56())
                .count();
        
        long volumeSatisfied = dataPoints.stream()
                .filter(dp -> dp.getFlattenedIndicators() != null)
                .filter(dp -> dp.getFlattenedIndicators().getVolume_5min_surge() != null && 
                             dp.getFlattenedIndicators().getVolume_5min_surge())
                .count();
        
        analysis.put("emaSatisfiedCount", emaSatisfied);
        analysis.put("rsiSatisfiedCount", rsiSatisfied);
        analysis.put("volumeSatisfiedCount", volumeSatisfied);
        analysis.put("emaSatisfiedPercentage", (double) emaSatisfied / dataPoints.size() * 100);
        analysis.put("rsiSatisfiedPercentage", (double) rsiSatisfied / dataPoints.size() * 100);
        analysis.put("volumeSatisfiedPercentage", (double) volumeSatisfied / dataPoints.size() * 100);
        
        return analysis;
    }
    
    /**
     * Analyze quality scores
     */
    private Map<String, Object> analyzeQuality(List<BacktestDataPoint> dataPoints) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Call quality statistics
        DoubleSummaryStatistics callQualityStats = dataPoints.stream()
                .mapToDouble(dp -> dp.getCallEntryQuality().getQualityScore())
                .summaryStatistics();
        
        // Put quality statistics
        DoubleSummaryStatistics putQualityStats = dataPoints.stream()
                .mapToDouble(dp -> dp.getPutEntryQuality().getQualityScore())
                .summaryStatistics();
        
        analysis.put("callQualityStats", Map.of(
                "min", callQualityStats.getMin(),
                "max", callQualityStats.getMax(),
                "avg", callQualityStats.getAverage()
        ));
        
        analysis.put("putQualityStats", Map.of(
                "min", putQualityStats.getMin(),
                "max", putQualityStats.getMax(),
                "avg", putQualityStats.getAverage()
        ));
        
        return analysis;
    }
    
    /**
     * Analyze time patterns
     */
    private Map<String, Object> analyzeTimePatterns(List<BacktestDataPoint> dataPoints) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Group by hour
        Map<Integer, Long> hourlyDistribution = dataPoints.stream()
                .collect(Collectors.groupingBy(
                        dp -> dp.getTimestamp().getHours(),
                        Collectors.counting()));
        
        analysis.put("hourlyDistribution", hourlyDistribution);
        
        // Find peak activity hours
        Optional<Map.Entry<Integer, Long>> peakHour = hourlyDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue());
        
        if (peakHour.isPresent()) {
            analysis.put("peakActivityHour", peakHour.get().getKey());
            analysis.put("peakActivityCount", peakHour.get().getValue());
        }
        
        return analysis;
    }
    
    /**
     * Generate recommendations
     */
    private Map<String, Object> generateRecommendations(List<BacktestDataPoint> dataPoints) {
        Map<String, Object> recommendations = new HashMap<>();
        
        // Calculate signal frequency
        long totalSignals = dataPoints.stream()
                .filter(dp -> dp.isShouldMakeCallEntry() || dp.isShouldMakePutEntry())
                .count();
        
        double signalFrequency = (double) totalSignals / dataPoints.size() * 100;
        
        if (signalFrequency > 20) {
            recommendations.put("signalFrequency", "HIGH - Consider tightening entry criteria");
        } else if (signalFrequency > 10) {
            recommendations.put("signalFrequency", "MODERATE - Current frequency looks good");
        } else {
            recommendations.put("signalFrequency", "LOW - Consider relaxing entry criteria");
        }
        
        // Quality recommendations
        double avgCallQuality = dataPoints.stream()
                .mapToDouble(dp -> dp.getCallEntryQuality().getQualityScore())
                .average()
                .orElse(0.0);
        
        double avgPutQuality = dataPoints.stream()
                .mapToDouble(dp -> dp.getPutEntryQuality().getQualityScore())
                .average()
                .orElse(0.0);
        
        if (avgCallQuality < 5.0) {
            recommendations.put("callQuality", "LOW - Review CALL entry criteria");
        } else {
            recommendations.put("callQuality", "GOOD - CALL quality is acceptable");
        }
        
        if (avgPutQuality < 5.0) {
            recommendations.put("putQuality", "LOW - Review PUT entry criteria");
        } else {
            recommendations.put("putQuality", "GOOD - PUT quality is acceptable");
        }
        
        return recommendations;
    }
}
