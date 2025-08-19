package com.jtradebot.processor.service;

import com.jtradebot.processor.model.indicator.FlattenedIndicators;
import com.zerodhatech.models.Tick;

/**
 * Service for analyzing market conditions and detecting flat/non-directional markets
 */
public interface MarketConditionAnalysisService {
    
    /**
     * Analyzes market conditions to determine if the market is flat or non-directional
     * @param tick Current tick data
     * @param indicators Flattened indicators
     * @return MarketConditionResult with analysis details
     */
    MarketConditionResult analyzeMarketCondition(Tick tick, FlattenedIndicators indicators);
    
    /**
     * Checks if the current market conditions are suitable for trading
     * @param tick Current tick data
     * @param indicators Flattened indicators
     * @return true if market conditions are suitable, false otherwise
     */
    boolean isMarketConditionSuitable(Tick tick, FlattenedIndicators indicators);
    
    /**
     * Calculates directional strength of the market
     * @param tick Current tick data
     * @param indicators Flattened indicators
     * @return Directional strength score (0.0 to 1.0)
     */
    double calculateDirectionalStrength(Tick tick, FlattenedIndicators indicators);
    
    /**
     * Analyzes candle characteristics to detect small/non-directional candles
     * @param tick Current tick data
     * @param indicators Flattened indicators
     * @return CandleAnalysisResult with candle characteristics
     */
    CandleAnalysisResult analyzeCandleCharacteristics(Tick tick, FlattenedIndicators indicators);
    
    /**
     * Calculates market volatility score
     * @param tick Current tick data
     * @param indicators Flattened indicators
     * @return Volatility score (0.0 to 1.0)
     */
    double calculateVolatilityScore(Tick tick, FlattenedIndicators indicators);
    
    /**
     * Get detailed flat market reason with actual values
     * @param tick The current tick data
     * @param indicators The flattened indicators
     * @return Detailed reason string with market condition details
     */
    String getDetailedFlatMarketReason(Tick tick, FlattenedIndicators indicators);
    
    /**
     * Get structured market condition details
     * @param tick The current tick data
     * @param indicators The flattened indicators
     * @return Map containing structured market condition details
     */
    java.util.Map<String, Object> getStructuredMarketConditionDetails(Tick tick, FlattenedIndicators indicators);
    
    /**
     * Result class for market condition analysis
     */
    class MarketConditionResult {
        private boolean isFlatMarket;
        private boolean isSuitableForTrading;
        private double directionalStrength;
        private double volatilityScore;
        private double candleSizeScore;
        private double overallScore;
        private String reason;
        private CandleAnalysisResult candleAnalysis;
        
        // Constructors, getters, setters
        public MarketConditionResult() {}
        
        public MarketConditionResult(boolean isFlatMarket, boolean isSuitableForTrading, 
                                   double directionalStrength, double volatilityScore, 
                                   double candleSizeScore, double overallScore, 
                                   String reason, CandleAnalysisResult candleAnalysis) {
            this.isFlatMarket = isFlatMarket;
            this.isSuitableForTrading = isSuitableForTrading;
            this.directionalStrength = directionalStrength;
            this.volatilityScore = volatilityScore;
            this.candleSizeScore = candleSizeScore;
            this.overallScore = overallScore;
            this.reason = reason;
            this.candleAnalysis = candleAnalysis;
        }
        
        // Getters and setters
        public boolean isFlatMarket() { return isFlatMarket; }
        public void setFlatMarket(boolean flatMarket) { isFlatMarket = flatMarket; }
        
        public boolean isSuitableForTrading() { return isSuitableForTrading; }
        public void setSuitableForTrading(boolean suitableForTrading) { isSuitableForTrading = suitableForTrading; }
        
        public double getDirectionalStrength() { return directionalStrength; }
        public void setDirectionalStrength(double directionalStrength) { this.directionalStrength = directionalStrength; }
        
        public double getVolatilityScore() { return volatilityScore; }
        public void setVolatilityScore(double volatilityScore) { this.volatilityScore = volatilityScore; }
        
        public double getCandleSizeScore() { return candleSizeScore; }
        public void setCandleSizeScore(double candleSizeScore) { this.candleSizeScore = candleSizeScore; }
        
        public double getOverallScore() { return overallScore; }
        public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public CandleAnalysisResult getCandleAnalysis() { return candleAnalysis; }
        public void setCandleAnalysis(CandleAnalysisResult candleAnalysis) { this.candleAnalysis = candleAnalysis; }
    }
    
    /**
     * Result class for candle analysis
     */
    class CandleAnalysisResult {
        private double candleHeight;
        private double bodyRatio;
        private boolean isDoji;
        private boolean isSpinningTop;
        private boolean isSmallBody;
        private boolean isLongBody;
        private int consecutiveSmallCandles;
        private int consecutiveDoji;
        private int consecutiveSpinningTop;
        private double averageCandleHeight;
        private double averageBodyRatio;
        
        // Constructors, getters, setters
        public CandleAnalysisResult() {}
        
        // Getters and setters
        public double getCandleHeight() { return candleHeight; }
        public void setCandleHeight(double candleHeight) { this.candleHeight = candleHeight; }
        
        public double getBodyRatio() { return bodyRatio; }
        public void setBodyRatio(double bodyRatio) { this.bodyRatio = bodyRatio; }
        
        public boolean isDoji() { return isDoji; }
        public void setDoji(boolean doji) { isDoji = doji; }
        
        public boolean isSpinningTop() { return isSpinningTop; }
        public void setSpinningTop(boolean spinningTop) { isSpinningTop = spinningTop; }
        
        public boolean isSmallBody() { return isSmallBody; }
        public void setSmallBody(boolean smallBody) { isSmallBody = smallBody; }
        
        public boolean isLongBody() { return isLongBody; }
        public void setLongBody(boolean longBody) { isLongBody = longBody; }
        
        public int getConsecutiveSmallCandles() { return consecutiveSmallCandles; }
        public void setConsecutiveSmallCandles(int consecutiveSmallCandles) { this.consecutiveSmallCandles = consecutiveSmallCandles; }
        
        public int getConsecutiveDoji() { return consecutiveDoji; }
        public void setConsecutiveDoji(int consecutiveDoji) { this.consecutiveDoji = consecutiveDoji; }
        
        public int getConsecutiveSpinningTop() { return consecutiveSpinningTop; }
        public void setConsecutiveSpinningTop(int consecutiveSpinningTop) { this.consecutiveSpinningTop = consecutiveSpinningTop; }
        
        public double getAverageCandleHeight() { return averageCandleHeight; }
        public void setAverageCandleHeight(double averageCandleHeight) { this.averageCandleHeight = averageCandleHeight; }
        
        public double getAverageBodyRatio() { return averageBodyRatio; }
        public void setAverageBodyRatio(double averageBodyRatio) { this.averageBodyRatio = averageBodyRatio; }
    }
}
