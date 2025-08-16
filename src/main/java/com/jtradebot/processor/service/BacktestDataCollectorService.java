package com.jtradebot.processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.jtradebot.processor.model.indicator.EntryQuality;
import com.jtradebot.processor.model.strategy.StrategyScore;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class BacktestDataCollectorService {
    
    private final ScalpingVolumeSurgeService scalpingVolumeSurgeService;
    private final ObjectMapper objectMapper;
    private final TickDataManager tickDataManager;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    
    // Configuration
    @Value("${jtradebot.backtest-data.enabled:false}")
    private boolean enabled;
    
    @Value("${jtradebot.backtest-data.batch-size:100}")
    private int batchSize;
    
    @Value("${jtradebot.backtest-data.batch-interval-ms:5000}")
    private long batchIntervalMs;
    
    @Value("${jtradebot.backtest-data.output-directory:./backtest-data}")
    private String outputDirectory;
    
    @Value("${jtradebot.backtest-data.session-prefix:auto_session}")
    private String sessionPrefix;
    
    @Value("${jtradebot.backtest-data.max-file-size-mb:50}")
    private long maxFileSizeMb;
    
    @Value("${jtradebot.backtest-data.ticks-per-file:1000}")
    private int ticksPerFile;
    
    // Batch processing
    private final BlockingQueue<BacktestDataPoint> dataQueue = new LinkedBlockingQueue<>();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    private volatile boolean isRunning = false;
    private String currentSessionId;
    private String currentFileName;
    private final AtomicLong dataPointCounter = new AtomicLong(0);
    private final AtomicLong fileCounter = new AtomicLong(0);
    private final AtomicLong tickCounterInFile = new AtomicLong(0);
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    @PostConstruct
    public void init() {
        if (enabled) {
            startAutomaticCollection();
        }
    }
    
    @PreDestroy
    public void cleanup() {
        stopAutomaticCollection();
    }
    
    /**
     * Start automatic data collection
     */
    private void startAutomaticCollection() {
        if (isRunning) {
            log.warn("Data collection is already running");
            return;
        }
        
        try {
            // Create output directory
            Path outputPath = Paths.get(outputDirectory);
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }
            
            // Note: Cleanup is now handled via API endpoint
            
            // Initialize session
            this.currentSessionId = sessionPrefix + "_" + System.currentTimeMillis();
            this.currentFileName = generateFileName();
            this.isRunning = true;
            this.dataPointCounter.set(0);
            this.fileCounter.set(0);
            this.tickCounterInFile.set(0);
            
            // Start batch processor
            startBatchProcessor();
            
            log.info("🚀 Started automatic backtest data collection for session: {}", currentSessionId);
            
        } catch (Exception e) {
            log.error("Error starting automatic data collection: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Stop automatic data collection
     */
    private void stopAutomaticCollection() {
        if (!isRunning) {
            return;
        }
        
        try {
            this.isRunning = false;
            
            // Process remaining data in queue
            processBatch();
            
            // Shutdown executors
            executorService.shutdown();
            scheduler.shutdown();
            
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            log.info("🛑 Stopped automatic backtest data collection. Total data points: {}", dataPointCounter.get());
            
        } catch (Exception e) {
            log.error("Error stopping automatic data collection: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Start batch processor
     */
    private void startBatchProcessor() {
        // Schedule periodic batch processing
        scheduler.scheduleAtFixedRate(() -> {
            if (isRunning && !dataQueue.isEmpty()) {
                processBatch();
            }
        }, batchIntervalMs, batchIntervalMs, TimeUnit.MILLISECONDS);
        
        // Start async batch processor
        executorService.submit(() -> {
            while (isRunning) {
                try {
                    List<BacktestDataPoint> batch = new ArrayList<>();
                    
                    // Collect batch
                    BacktestDataPoint first = dataQueue.poll(1, TimeUnit.SECONDS);
                    if (first != null) {
                        batch.add(first);
                        dataQueue.drainTo(batch, batchSize - 1);
                        
                        // Process batch
                        writeBatchToFile(batch);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in batch processor: {}", e.getMessage(), e);
                }
            }
        });
    }
    
    /**
     * Process current batch
     */
    private void processBatch() {
        List<BacktestDataPoint> batch = new ArrayList<>();
        dataQueue.drainTo(batch);
        
        if (!batch.isEmpty()) {
            writeBatchToFile(batch);
        }
    }
    
    /**
     * Collect data point (called from TickProcessService)
     * Future data will be fetched from map when needed
     */
    public void collectTickData(Tick indexTick) {
        if (!enabled || !isRunning) return;
        
        try {
            BacktestDataPoint dataPoint = createDataPoint(indexTick);
            
            // Add to queue for async processing
            if (!dataQueue.offer(dataPoint)) {
                log.warn("Data queue is full, dropping data point");
            }
            
        } catch (Exception e) {
            log.error("Error collecting tick data: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Create data point from tick data
     * Future data will be fetched from map when needed
     */
    private BacktestDataPoint createDataPoint(Tick indexTick) {
        BacktestDataPoint dataPoint = new BacktestDataPoint();
        dataPoint.setId(dataPointCounter.incrementAndGet());
        dataPoint.setTimestamp(indexTick.getTickTimestamp());
        dataPoint.setSessionId(currentSessionId);
        
        // Get future tick from map for volume data
        String niftyFutureToken = kiteInstrumentHandler.getNifty50FutureToken().toString();
        Tick futureTick = tickDataManager.getLastTick(niftyFutureToken);
        
        // Basic tick data
        dataPoint.setIndexPrice(indexTick.getLastTradedPrice());
        dataPoint.setFuturePrice(futureTick != null ? futureTick.getLastTradedPrice() : 0.0);
        dataPoint.setIndexVolume(indexTick.getVolumeTradedToday());
        dataPoint.setFutureVolume(futureTick != null ? futureTick.getVolumeTradedToday() : 0L);
        dataPoint.setHighPrice(indexTick.getHighPrice());
        dataPoint.setLowPrice(indexTick.getLowPrice());
        dataPoint.setOpenPrice(indexTick.getOpenPrice());
        dataPoint.setClosePrice(indexTick.getClosePrice());
        
        // Get all indicators - future data will be fetched from map when needed
        FlattenedIndicators indicators = scalpingVolumeSurgeService.getFlattenedIndicators(indexTick);
        dataPoint.setFlattenedIndicators(indicators);
        
        // Get entry quality analysis
        EntryQuality callQuality = evaluateCallEntryQuality(indicators, indexTick);
        EntryQuality putQuality = evaluatePutEntryQuality(indicators, indexTick);
        dataPoint.setCallEntryQuality(callQuality);
        dataPoint.setPutEntryQuality(putQuality);
        
        // Get strategy decisions
        boolean shouldCall = scalpingVolumeSurgeService.shouldMakeCallEntry(indexTick);
        boolean shouldPut = scalpingVolumeSurgeService.shouldMakePutEntry(indexTick);
        dataPoint.setShouldMakeCallEntry(shouldCall);
        dataPoint.setShouldMakePutEntry(shouldPut);
        
        // Get strategy score
        StrategyScore strategyScore = scalpingVolumeSurgeService.calculateStrategyScore(indexTick);
        dataPoint.setStrategyScore(strategyScore);
        
        // Calculate trend strength
        TrendAnalysis trendAnalysis = calculateTrendStrength(indicators);
        dataPoint.setTrendAnalysis(trendAnalysis);
        
        return dataPoint;
    }
    
    /**
     * Write batch to file
     */
    private void writeBatchToFile(List<BacktestDataPoint> batch) {
        if (batch.isEmpty()) return;
        
        try {
            // Check if we need to create a new file
            if (currentFileName == null || tickCounterInFile.get() >= ticksPerFile) {
                currentFileName = generateFileName();
                tickCounterInFile.set(0);
                log.info("📄 Creating new file: {}", currentFileName);
            }
            
            // Load existing data from file if it exists
            Map<String, List<BacktestDataPoint>> existingData = new HashMap<>();
            File currentFile = new File(currentFileName);
            if (currentFile.exists()) {
                try {
                    existingData = objectMapper.readValue(currentFile, 
                            objectMapper.getTypeFactory().constructMapType(
                                    HashMap.class, String.class, List.class));
                } catch (Exception e) {
                    log.warn("Could not load existing data from file: {}", e.getMessage());
                }
            }
            
            // Group new data by session
            Map<String, List<BacktestDataPoint>> newData = batch.stream()
                    .collect(Collectors.groupingBy(BacktestDataPoint::getSessionId));
            
            // Merge with existing data
            for (Map.Entry<String, List<BacktestDataPoint>> entry : newData.entrySet()) {
                String sessionId = entry.getKey();
                List<BacktestDataPoint> newPoints = entry.getValue();
                
                if (existingData.containsKey(sessionId)) {
                    existingData.get(sessionId).addAll(newPoints);
                } else {
                    existingData.put(sessionId, newPoints);
                }
            }
            
            // Write merged data to JSON
            writeBatchToJson(existingData);
            
            // Write to CSV
            writeBatchToCsv(batch);
            
            // Update counters
            tickCounterInFile.addAndGet(batch.size());
            dataPointCounter.addAndGet(batch.size());
            
            log.debug("💾 Wrote {} data points to file. Total: {}", batch.size(), dataPointCounter.get());
            
        } catch (Exception e) {
            log.error("Error writing batch to file: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Write batch to JSON file
     */
    private void writeBatchToJson(Map<String, List<BacktestDataPoint>> sessionData) {
        try {
            File file = new File(currentFileName);
            objectMapper.writeValue(file, sessionData);
            
        } catch (Exception e) {
            log.error("Error writing batch to JSON: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Write batch to CSV file
     */
    private void writeBatchToCsv(List<BacktestDataPoint> batch) {
        try {
            String csvFileName = currentFileName.replace(".json", "_summary.csv");
            File csvFile = new File(csvFileName);
            boolean isNewFile = !csvFile.exists();
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile, true))) {
                // Write header if new file
                if (isNewFile) {
                    writer.println("Timestamp,IndexPrice,FuturePrice,Volume,EMA_Signals,RSI_Signals,Volume_Signals,Total_Signals,Trend_Strength,Call_Quality,Put_Quality,Should_Call,Should_Put,Strategy_Score");
                }
                
                // Write batch data
                for (BacktestDataPoint point : batch) {
                    writer.printf("%s,%.2f,%.2f,%d,%d,%d,%d,%d,%s,%.2f,%.2f,%s,%s,%.2f%n",
                            point.getTimestamp(),
                            point.getIndexPrice(),
                            point.getFuturePrice(),
                            point.getIndexVolume(),
                            point.getTrendAnalysis().getEmaSignals(),
                            point.getTrendAnalysis().getRsiSignals(),
                            point.getTrendAnalysis().getVolumeSignals(),
                            point.getTrendAnalysis().getTotalSignals(),
                            point.getTrendAnalysis().getTrendStrength(),
                            point.getCallEntryQuality().getQualityScore(),
                            point.getPutEntryQuality().getQualityScore(),
                            point.isShouldMakeCallEntry(),
                            point.isShouldMakePutEntry(),
                            point.getStrategyScore().getScore());
                }
            }
            
        } catch (Exception e) {
            log.error("Error writing batch to CSV: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Clean backtest directory (public method for API access)
     */
    public Map<String, Object> cleanBacktestDirectory() {
        Map<String, Object> result = new HashMap<>();
        try {
            Path outputPath = Paths.get(outputDirectory);
            if (!Files.exists(outputPath)) {
                result.put("success", true);
                result.put("message", "Backtest directory does not exist: " + outputDirectory);
                result.put("filesDeleted", 0);
                return result;
            }
            
            List<String> deletedFiles = new ArrayList<>();
            Files.walk(outputPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json") || path.toString().endsWith(".csv"))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            deletedFiles.add(path.getFileName().toString());
                            log.debug("🗑️ Deleted old file: {}", path);
                        } catch (IOException e) {
                            log.warn("⚠️ Could not delete file: {}", path);
                        }
                    });
            
            result.put("success", true);
            result.put("message", "Successfully cleaned backtest directory: " + outputDirectory);
            result.put("filesDeleted", deletedFiles.size());
            result.put("deletedFiles", deletedFiles);
            
            log.info("🧹 Cleaned backtest directory: {} ({} files deleted)", outputDirectory, deletedFiles.size());
            
        } catch (Exception e) {
            log.error("Error cleaning backtest directory: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "Failed to clean directory: " + e.getMessage());
            result.put("filesDeleted", 0);
        }
        
        return result;
    }
    
    /**
     * Generate file name
     */
    private String generateFileName() {
        String timestamp = LocalDateTime.now().format(formatter);
        return String.format("%s/backtest_data_%s_%s_%d.json", 
                outputDirectory, currentSessionId, timestamp, fileCounter.incrementAndGet());
    }
    
    /**
     * Evaluate CALL entry quality (copied from ScalpingVolumeSurgeServiceImpl)
     */
    private EntryQuality evaluateCallEntryQuality(FlattenedIndicators indicators, Tick tick) {
        EntryQuality quality = EntryQuality.builder()
                .instrumentToken(String.valueOf(tick.getInstrumentToken()))
                .timestamp(tick.getTickTimestamp().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
                .lastTradedPrice(tick.getLastTradedPrice())
                .entryType("CALL")
                .build();
        
        // EMA Quality Score (0-10)
        double emaScore = 0.0;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && indicators.getEma9_5min_gt_ema21_5min()) emaScore += 5.0;
        if (indicators.getEma9_1min_gt_ema21_1min() != null && indicators.getEma9_1min_gt_ema21_1min()) emaScore += 5.0;
        quality.setEmaScore(emaScore);
        
        // RSI Quality Score (0-10)
        double rsiScore = 0.0;
        if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) rsiScore += 5.0;
        if (indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) rsiScore += 5.0;
        quality.setRsiScore(rsiScore);
        
        // Volume Quality Score (0-10)
        double volumeScore = 0.0;
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) volumeScore += 5.0;
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) volumeScore += 3.0;
        if (indicators.getVolume_surge_multiplier() != null && indicators.getVolume_surge_multiplier() >= 3.0) volumeScore += 2.0;
        quality.setVolumeScore(volumeScore);
        
        // Price Action Quality Score (0-10)
        double priceActionScore = 0.0;
        if (indicators.getPrice_gt_vwap_5min() != null && indicators.getPrice_gt_vwap_5min()) priceActionScore += 5.0;
        if (indicators.getPrice_above_resistance() != null && indicators.getPrice_above_resistance()) priceActionScore += 5.0;
        quality.setPriceActionScore(priceActionScore);
        
        // Calculate overall quality score
        quality.calculateQualityScore();
        
        return quality;
    }
    
    /**
     * Evaluate PUT entry quality (copied from ScalpingVolumeSurgeServiceImpl)
     */
    private EntryQuality evaluatePutEntryQuality(FlattenedIndicators indicators, Tick tick) {
        EntryQuality quality = EntryQuality.builder()
                .instrumentToken(String.valueOf(tick.getInstrumentToken()))
                .timestamp(tick.getTickTimestamp().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
                .lastTradedPrice(tick.getLastTradedPrice())
                .entryType("PUT")
                .build();
        
        // EMA Quality Score (0-10)
        double emaScore = 0.0;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && !indicators.getEma9_5min_gt_ema21_5min()) emaScore += 5.0;
        if (indicators.getEma9_1min_gt_ema21_1min() != null && !indicators.getEma9_1min_gt_ema21_1min()) emaScore += 5.0;
        quality.setEmaScore(emaScore);
        
        // RSI Quality Score (0-10)
        double rsiScore = 0.0;
        if (indicators.getRsi_5min_lt_44() != null && indicators.getRsi_5min_lt_44()) rsiScore += 5.0;
        if (indicators.getRsi_1min_lt_44() != null && indicators.getRsi_1min_lt_44()) rsiScore += 5.0;
        quality.setRsiScore(rsiScore);
        
        // Volume Quality Score (0-10)
        double volumeScore = 0.0;
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) volumeScore += 5.0;
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) volumeScore += 3.0;
        if (indicators.getVolume_surge_multiplier() != null && indicators.getVolume_surge_multiplier() >= 3.0) volumeScore += 2.0;
        quality.setVolumeScore(volumeScore);
        
        // Price Action Quality Score (0-10)
        double priceActionScore = 0.0;
        if (indicators.getPrice_lt_vwap_5min() != null && indicators.getPrice_lt_vwap_5min()) priceActionScore += 5.0;
        if (indicators.getPrice_below_support() != null && indicators.getPrice_below_support()) priceActionScore += 5.0;
        quality.setPriceActionScore(priceActionScore);
        
        // Calculate overall quality score
        quality.calculateQualityScore();
        
        return quality;
    }
    
    /**
     * Calculate trend strength analysis
     */
    private TrendAnalysis calculateTrendStrength(FlattenedIndicators indicators) {
        TrendAnalysis analysis = new TrendAnalysis();
        
        // Count strong signals
        int strongEmaSignals = 0;
        if (indicators.getEma9_1min_gt_ema21_1min() != null && indicators.getEma9_1min_gt_ema21_1min()) strongEmaSignals++;
        if (indicators.getEma9_5min_gt_ema21_5min() != null && indicators.getEma9_5min_gt_ema21_5min()) strongEmaSignals++;
        if (indicators.getEma9_15min_gt_ema21_15min() != null && indicators.getEma9_15min_gt_ema21_15min()) strongEmaSignals++;
        
        int strongRsiSignals = 0;
        if (indicators.getRsi_1min_gt_56() != null && indicators.getRsi_1min_gt_56()) strongRsiSignals++;
        if (indicators.getRsi_5min_gt_56() != null && indicators.getRsi_5min_gt_56()) strongRsiSignals++;
        if (indicators.getRsi_15min_gt_56() != null && indicators.getRsi_15min_gt_56()) strongRsiSignals++;
        
        int strongVolumeSignals = 0;
        if (indicators.getVolume_1min_surge() != null && indicators.getVolume_1min_surge()) strongVolumeSignals++;
        if (indicators.getVolume_5min_surge() != null && indicators.getVolume_5min_surge()) strongVolumeSignals++;
        if (indicators.getVolume_15min_surge() != null && indicators.getVolume_15min_surge()) strongVolumeSignals++;
        
        analysis.setEmaSignals(strongEmaSignals);
        analysis.setRsiSignals(strongRsiSignals);
        analysis.setVolumeSignals(strongVolumeSignals);
        analysis.setTotalSignals(strongEmaSignals + strongRsiSignals + strongVolumeSignals);
        
        // Determine trend strength
        if (analysis.getTotalSignals() >= 7) analysis.setTrendStrength("VERY_STRONG");
        else if (analysis.getTotalSignals() >= 5) analysis.setTrendStrength("STRONG");
        else if (analysis.getTotalSignals() >= 3) analysis.setTrendStrength("MODERATE");
        else analysis.setTrendStrength("WEAK");
        
        return analysis;
    }
    
    /**
     * Data point class for storing comprehensive backtest data
     */
    public static class BacktestDataPoint {
        private Long id;
        private String sessionId;
        private java.util.Date timestamp;
        private Double indexPrice;
        private Double futurePrice;
        private Long indexVolume;
        private Long futureVolume;
        private Double highPrice;
        private Double lowPrice;
        private Double openPrice;
        private Double closePrice;
        private FlattenedIndicators flattenedIndicators;
        private EntryQuality callEntryQuality;
        private EntryQuality putEntryQuality;
        private boolean shouldMakeCallEntry;
        private boolean shouldMakePutEntry;
        private StrategyScore strategyScore;
        private TrendAnalysis trendAnalysis;
        
        // Getters and setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        
        public java.util.Date getTimestamp() { return timestamp; }
        public void setTimestamp(java.util.Date timestamp) { this.timestamp = timestamp; }
        
        public Double getIndexPrice() { return indexPrice; }
        public void setIndexPrice(Double indexPrice) { this.indexPrice = indexPrice; }
        
        public Double getFuturePrice() { return futurePrice; }
        public void setFuturePrice(Double futurePrice) { this.futurePrice = futurePrice; }
        
        public Long getIndexVolume() { return indexVolume; }
        public void setIndexVolume(Long indexVolume) { this.indexVolume = indexVolume; }
        
        public Long getFutureVolume() { return futureVolume; }
        public void setFutureVolume(Long futureVolume) { this.futureVolume = futureVolume; }
        
        public Double getHighPrice() { return highPrice; }
        public void setHighPrice(Double highPrice) { this.highPrice = highPrice; }
        
        public Double getLowPrice() { return lowPrice; }
        public void setLowPrice(Double lowPrice) { this.lowPrice = lowPrice; }
        
        public Double getOpenPrice() { return openPrice; }
        public void setOpenPrice(Double openPrice) { this.openPrice = openPrice; }
        
        public Double getClosePrice() { return closePrice; }
        public void setClosePrice(Double closePrice) { this.closePrice = closePrice; }
        
        public FlattenedIndicators getFlattenedIndicators() { return flattenedIndicators; }
        public void setFlattenedIndicators(FlattenedIndicators flattenedIndicators) { this.flattenedIndicators = flattenedIndicators; }
        
        public EntryQuality getCallEntryQuality() { return callEntryQuality; }
        public void setCallEntryQuality(EntryQuality callEntryQuality) { this.callEntryQuality = callEntryQuality; }
        
        public EntryQuality getPutEntryQuality() { return putEntryQuality; }
        public void setPutEntryQuality(EntryQuality putEntryQuality) { this.putEntryQuality = putEntryQuality; }
        
        public boolean isShouldMakeCallEntry() { return shouldMakeCallEntry; }
        public void setShouldMakeCallEntry(boolean shouldMakeCallEntry) { this.shouldMakeCallEntry = shouldMakeCallEntry; }
        
        public boolean isShouldMakePutEntry() { return shouldMakePutEntry; }
        public void setShouldMakePutEntry(boolean shouldMakePutEntry) { this.shouldMakePutEntry = shouldMakePutEntry; }
        
        public StrategyScore getStrategyScore() { return strategyScore; }
        public void setStrategyScore(StrategyScore strategyScore) { this.strategyScore = strategyScore; }
        
        public TrendAnalysis getTrendAnalysis() { return trendAnalysis; }
        public void setTrendAnalysis(TrendAnalysis trendAnalysis) { this.trendAnalysis = trendAnalysis; }
    }
    
    /**
     * Trend analysis class
     */
    public static class TrendAnalysis {
        private int emaSignals;
        private int rsiSignals;
        private int volumeSignals;
        private int totalSignals;
        private String trendStrength;
        
        // Getters and setters
        public int getEmaSignals() { return emaSignals; }
        public void setEmaSignals(int emaSignals) { this.emaSignals = emaSignals; }
        
        public int getRsiSignals() { return rsiSignals; }
        public void setRsiSignals(int rsiSignals) { this.rsiSignals = rsiSignals; }
        
        public int getVolumeSignals() { return volumeSignals; }
        public void setVolumeSignals(int volumeSignals) { this.volumeSignals = volumeSignals; }
        
        public int getTotalSignals() { return totalSignals; }
        public void setTotalSignals(int totalSignals) { this.totalSignals = totalSignals; }
        
        public String getTrendStrength() { return trendStrength; }
        public void setTrendStrength(String trendStrength) { this.trendStrength = trendStrength; }
    }
}
