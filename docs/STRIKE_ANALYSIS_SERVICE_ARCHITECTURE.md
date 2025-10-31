# Strike Analysis Service Architecture Proposal

## Current Flow Analysis

### 1. TickOrchestrationService Flow

**Entry Point:** `TickOrchestrationService.processLiveTicks()`

**Flow for Nifty Index Ticks:**
1. **Step 1:** Calculate Flattened Indicators (`DynamicRuleEvaluatorService.getFlattenedIndicators()`)
   - Index-based indicators (EMA, RSI, MACD, candlestick patterns)
   - Future-based indicators (Volume, OI, price-volume surge)
   
2. **Step 2:** NTP Filters Check (`UnstableMarketConditionAnalysisService.checkFlexibleFilteringConditions()`)
   - Checks no-trade-zone filters with NTP (No Trade Points) system
   - Each filter has an NTP value
   - Total NTP must not exceed `maxAllowedNTP` from scenarios
   - Returns `FlexibleFilteringResult` with pass/fail status

3. **Step 3:** Category Scoring (`MarketDirectionService.getDetailedCategoryScores()`)
   - Calculates detailed category scores for CALL and PUT
   - Categories: EMA, FutureAndVolume, Candlestick, Momentum
   - Each category has weighted indicators
   - Returns `Map<String, DetailedCategoryScore>`

4. **Step 4:** Quality Score Calculation
   - `calculateQualityScore(callTotal, putTotal)`
   - Formula: `(winningScore / totalPossibleScore) * 10.0`
   - Determines dominant trend (CALL or PUT)

5. **Step 5-6:** Entry Blocking Checks
   - Block after recent stop-loss hits (1-min candle)
   - Block after manual exit (5-min candle)

6. **Step 7:** Entry Eligibility Check (`isEligibleForEntryCheck()`)
   - Checks if in trading zone (NTP filters passed)
   - Checks if quality score >= minimum threshold

7. **Step 8:** Order Entry (`OrderManagementService.entryOrder()`)
   - Gets entry decision from `DynamicRuleEvaluatorService`
   - Creates order via `OrderManagementService.createTradeOrder()`
   - **Current Strike Selection:**
     - Uses `GreeksAnalysisService.getBestStrikeForScalping()` for both live and local
     - `GreeksAnalysisService` already:
       - Analyzes ±200 strike prices around current index
       - Calculates Greeks for each strike (Delta, Gamma, Theta, Vega, IV)
       - Scores each strike (delta, price, moneyness, IV, theta)
       - Caches results with price/time-based refresh

8. **Step 9:** Active Order Management
   - Updates live P&L
   - Checks exit conditions (stop loss, target, strategy reversal)
   - Executes exits

### 2. Current Strike Selection (GreeksAnalysisService)

**Location:** `GreeksAnalysisService.getBestStrikeForScalping()`

**Current Capabilities:**
- Analyzes ±200 strikes around current index price
- Calculates Greeks using `OptionGreeksCalculator`
- Gets LTP from Kite API
- Scores strikes using:
  - Delta (30% weight)
  - Price (25% weight)
  - Moneyness (20% weight)
  - IV (15% weight)
  - Theta (10% weight)
- Caches results (refreshes on price change ≥10 points or every 10 seconds)

**What's Missing:**
- No option chain analysis (OI, volume trends)
- No additional risk filters for entry timing
- No subscription to live tick data for strikes
- No additional Call/Put scoring based on option chain data

---

## Proposed New Service: StrikeAnalysisService

### Overview

A separate service that:
1. Fetches latest Index price
2. Analyzes ±200 strike prices with comprehensive option chain data
3. Subscribes to live tick data for selected strikes via Kite
4. Performs additional Call/Put scoring with option chain analysis
5. Applies additional risk filters for entry timing
6. Determines best strike price with enriched data
7. Publishes results to Kafka topic for consumption by current service

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│              StrikeAnalysisService (New)                     │
│                                                             │
│  1. Fetches Index Price (Nifty50)                          │
│  2. Analyzes ±200 Strikes                                   │
│  3. Subscribes to Kite Tick Stream for Strikes             │
│  4. Option Chain Analysis (OI, Volume, Greeks)             │
│  5. Additional Call/Put Scoring                             │
│  6. Risk Zone Filter (Entry Timing)                        │
│  7. Best Strike Selection                                   │
│  8. Kafka Producer → Send to Topic                          │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ Kafka Topic: "strike_analysis_results"
                           ▼
┌─────────────────────────────────────────────────────────────┐
│        Current Service (JtradebotProcessor)                  │
│                                                             │
│  Kafka Consumer → Receives Strike Analysis Results          │
│  Skips Greeks Calculation                                   │
│  Uses Pre-calculated Best Strike                            │
└─────────────────────────────────────────────────────────────┘
```

### Service Components

#### 1. Index Price Service
```java
public interface IndexPriceService {
    double getCurrentNiftyIndexPrice();
    Tick getCurrentNiftyTick();
}
```

#### 2. Strike Subscription Service
```java
public interface StrikeSubscriptionService {
    void subscribeToStrikes(List<Long> instrumentTokens);
    void unsubscribeFromStrikes(List<Long> instrumentTokens);
    Map<Long, Tick> getLiveTickData();
}
```

#### 3. Option Chain Analysis Service
```java
public interface OptionChainAnalysisService {
    OptionChainData analyzeOptionChain(String expiry, double indexPrice);
    Map<Integer, StrikeChainData> getStrikeChainData(int minStrike, int maxStrike);
}
```

#### 4. Enhanced Scoring Service
```java
public interface EnhancedScoringService {
    CallPutScoreResult calculateCallPutScores(
        double indexPrice,
        Map<Integer, StrikeChainData> callStrikes,
        Map<Integer, StrikeChainData> putStrikes
    );
}
```

#### 5. Risk Zone Filter Service
```java
public interface RiskZoneFilterService {
    RiskZoneResult checkRiskZones(double indexPrice, String optionType);
    boolean isSafeToEnter(String optionType, long timestamp);
}
```

#### 6. Best Strike Selection Service
```java
public interface BestStrikeSelectionService {
    BestStrikeResult findBestStrike(
        double indexPrice,
        String optionType,
        CallPutScoreResult scores,
        RiskZoneResult riskResult
    );
}
```

#### 7. Kafka Producer Service
```java
public interface StrikeAnalysisKafkaProducer {
    void sendStrikeAnalysisResult(StrikeAnalysisResult result);
}
```

### Data Models

#### StrikeAnalysisResult (Kafka Message)
```java
@Data
@Builder
public class StrikeAnalysisResult {
    private double niftyIndexPrice;
    private long timestamp;
    
    // Call Analysis
    private BestStrikeResult bestCallStrike;
    private CallPutScoreResult callScores;
    
    // Put Analysis
    private BestStrikeResult bestPutStrike;
    private CallPutScoreResult putScores;
    
    // Risk Analysis
    private RiskZoneResult riskZoneResult;
    
    // Option Chain Summary
    private OptionChainSummary optionChainSummary;
}
```

#### BestStrikeResult
```java
@Data
@Builder
public class BestStrikeResult {
    private String tradingSymbol;
    private int strikePrice;
    private Long instrumentToken;
    private String expiry;
    private double optionPrice;
    private double lastTradedPrice;
    
    // Greeks (from option chain)
    private double delta;
    private double gamma;
    private double theta;
    private double vega;
    private double impliedVolatility;
    
    // Option Chain Metrics
    private Long openInterest;
    private Long volume;
    private double oiChange;
    private double volumeChange;
    
    // Scores
    private double totalScore;
    private Map<String, Double> scoreBreakdown;
    
    // Risk Metrics
    private double riskScore;
    private boolean isSafeToEnter;
}
```

#### CallPutScoreResult
```java
@Data
@Builder
public class CallPutScoreResult {
    private String optionType; // CALL or PUT
    private double totalScore;
    private Map<Integer, StrikeScore> strikeScores; // Strike -> Score
    private String bestStrikeRecommendation;
    private Map<String, Double> categoryScores; // e.g., "OI_Momentum", "Volume_Surge", "Greeks_Score"
}
```

#### RiskZoneResult
```java
@Data
@Builder
public class RiskZoneResult {
    private boolean isSafeToEnter;
    private List<String> riskReasons;
    private double riskScore; // 0-100, lower is better
    private Map<String, Boolean> riskChecks; // e.g., "High_Volatility", "Expiry_Day", "News_Event"
}
```

### Integration Points

#### 1. Kafka Topic Configuration
```yaml
# application.yml
spring:
  kafka:
    topics:
      strike-analysis-results: jtrade_advance_strike_analysis
```

#### 2. Kafka Consumer in Current Service
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class StrikeAnalysisConsumer {
    
    @KafkaListener(topics = "${spring.kafka.topics.strike-analysis-results}")
    public void consumeStrikeAnalysis(StrikeAnalysisResult result) {
        // Store in cache for order entry
        strikeAnalysisCache.update(result);
    }
}
```

#### 3. Modification to OrderManagementService
```java
// In OrderManagementService.createTradeOrder()
// Instead of:
// GreeksAnalysisService.BestStrikeResult bestStrikeResult = 
//     greeksAnalysisService.getBestStrikeForScalping(optionType);

// Use:
BestStrikeResult bestStrike = strikeAnalysisCache.getBestStrike(optionType);
if (bestStrike == null) {
    // Fallback to current GreeksAnalysisService
    bestStrikeResult = greeksAnalysisService.getBestStrikeForScalping(optionType);
}
```

### Benefits

1. **Separation of Concerns**
   - Heavy strike analysis separated from order execution logic
   - Can scale independently

2. **Rich Data Analysis**
   - Option chain analysis (OI, Volume trends)
   - Additional risk filters
   - More comprehensive scoring

3. **Performance**
   - Pre-calculated strikes reduce latency in order entry
   - Can batch process multiple strikes efficiently

4. **Flexibility**
   - Can update analysis logic without touching order execution
   - Can add ML models for strike selection

5. **Observability**
   - Separate monitoring for strike analysis vs order execution
   - Easier to debug strike selection issues

### Implementation Steps

1. **Phase 1: New Service Setup**
   - Create new Spring Boot service
   - Setup Kite connection and subscription
   - Implement basic strike analysis

2. **Phase 2: Option Chain Integration**
   - Integrate option chain API
   - Implement OI/Volume analysis
   - Add enhanced scoring

3. **Phase 3: Risk Filters**
   - Implement risk zone detection
   - Add entry timing filters
   - Integrate with market events

4. **Phase 4: Kafka Integration**
   - Setup Kafka producer
   - Implement consumer in current service
   - Update OrderManagementService to use cached results

5. **Phase 5: Testing & Validation**
   - Compare results with current GreeksAnalysisService
   - Validate strike selection accuracy
   - Performance testing

### Configuration

```yaml
strike-analysis:
  strike-range: 200  # ±200 strikes
  refresh-interval-ms: 5000  # 5 seconds
  cache-ttl-seconds: 10
  risk-filter:
    enabled: true
    high-volatility-threshold: 25.0
    expiry-day-blocking: true
  scoring:
    weights:
      greeks: 0.30
      oi-momentum: 0.25
      volume-surge: 0.20
      price-action: 0.15
      risk-score: 0.10
```

---

## Answer to Your Question

**YES, this architecture is feasible and recommended!**

The new service can:
1. ✅ Fetch latest Index price
2. ✅ Analyze ±200 strikes with option chain data
3. ✅ Subscribe to strikes in Kite for live tick data
4. ✅ Perform additional Call/Put scoring
5. ✅ Apply additional risk filters for entry timing
6. ✅ Find best strike price with enriched data
7. ✅ Send all data via Kafka to current service

The current service can then:
- Skip Greeks calculation (already done in new service)
- Use pre-calculated best strike from Kafka
- Focus on order execution and risk management

**Key Integration Point:**
- Modify `OrderManagementService.createTradeOrder()` to check Kafka cache first
- Fallback to current `GreeksAnalysisService` if cache is empty

