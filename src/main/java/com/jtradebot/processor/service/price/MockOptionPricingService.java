package com.jtradebot.processor.service.price;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockOptionPricingService {

    private final OIAnalysisService oiAnalysisService;


    public Double calculateCurrentLTP(Double entryPrice, Double entryIndexPrice, Double currentIndexPrice, OrderTypeEnum orderType) {
        if (entryPrice == null || entryIndexPrice == null || currentIndexPrice == null) {
            log.warn("Invalid parameters for LTP calculation: entryPrice={}, entryIndexPrice={}, currentIndexPrice={}",
                    entryPrice, entryIndexPrice, currentIndexPrice);
            return entryPrice; // Return entry price if any value is null
        }

        // Try to use OI-based pricing first (simplified - no Greeks available)
        // Note: OI-based selection doesn't provide Greeks, so we use simple calculation

        // Fallback to simple index movement calculation
        return calculateCurrentLTPSimple(entryPrice, entryIndexPrice, currentIndexPrice, orderType);
    }

    // Note: Greeks-based pricing removed - OI-based selection doesn't provide Greeks
    // Using simple index movement calculation instead

    /**
     * Fallback method for simple index movement calculation
     * Uses more realistic Delta approximation based on option price
     */
    private Double calculateCurrentLTPSimple(Double entryPrice, Double entryIndexPrice, Double currentIndexPrice, OrderTypeEnum orderType) {
        // Calculate index movement
        Double indexMovement = currentIndexPrice - entryIndexPrice;

        // Estimate Delta based on option price (higher price = higher Delta)
        // This is a rough approximation: Delta ≈ 0.3 to 0.7 for typical options
        double estimatedDelta;
        if (entryPrice < 50) {
            estimatedDelta = 0.3; // Low price = low Delta (OTM)
        } else if (entryPrice < 150) {
            estimatedDelta = 0.5; // Medium price = medium Delta (ATM-ish)
        } else if (entryPrice < 300) {
            estimatedDelta = 0.7; // High price = high Delta (ITM)
        } else {
            estimatedDelta = 0.8; // Very high price = very high Delta (deep ITM)
        }

        // Apply Delta to index movement
        double deltaPriceChange = estimatedDelta * indexMovement;
        
        double currentLTP;
        if (orderType == OrderTypeEnum.CALL_BUY) {
            // CALL: option price increases when index increases
            currentLTP = entryPrice + deltaPriceChange;
        } else if (orderType == OrderTypeEnum.PUT_BUY) {
            // PUT: option price decreases when index increases (negative Delta)
            currentLTP = entryPrice - deltaPriceChange;
        } else {
            log.warn("Unsupported order type for LTP calculation: {}", orderType);
            return entryPrice;
        }

        // Ensure LTP doesn't go below 0 (minimum option price)
        currentLTP = Math.max(currentLTP, 0.0);

        log.debug("Simple LTP calculation: {} (Entry: {}, Index Movement: {}, Estimated Delta: {}, " +
                "Price Change: {}, Order Type: {}, Movement Ratio: {}x)",
                currentLTP, entryPrice, indexMovement, String.format("%.2f", estimatedDelta), 
                String.format("%.2f", deltaPriceChange), orderType,
                String.format("%.1f", Math.abs(deltaPriceChange) / Math.abs(indexMovement)));
        return currentLTP;
    }


    public double calculateEntryLTP(Double currentIndexPrice) {
        // Try to use OI-based entry pricing first
        try {
            Double oiBasedEntryPrice = calculateEntryLTPWithOI(currentIndexPrice);
            if (oiBasedEntryPrice != null) {
                log.debug("Using OI-based entry pricing: {} (Index: {})", oiBasedEntryPrice, currentIndexPrice);
                return oiBasedEntryPrice;
            }
        } catch (Exception e) {
            log.warn("OI-based entry pricing failed, falling back to simple calculation: {}", e.getMessage());
        }
        
        // Fallback to simple percentage-based calculation
        return currentIndexPrice * 0.005; // Changed from 0.01 (1%) to 0.005 (0.5%)
    }

    /**
     * Calculate entry LTP using OI-based pricing
     * This provides more realistic entry prices based on current market conditions
     */
    private Double calculateEntryLTPWithOI(Double currentIndexPrice) {
        try {
            // Get OI analysis for both CE and PE to find the best strike
            OIAnalysisService.BestStrikeResult callResult = oiAnalysisService.getBestStrikeForScalping("CE");
            OIAnalysisService.BestStrikeResult putResult = oiAnalysisService.getBestStrikeForScalping("PE");
            
            // Use the strike with better pricing (lower price for entry)
            OIAnalysisService.StrikeOIData bestStrike = null;
            if (callResult.isSuccess() && callResult.getBestStrike() != null && 
                putResult.isSuccess() && putResult.getBestStrike() != null) {
                
                // Choose the option with lower price for entry
                if (callResult.getBestStrike().getOptionPrice() < putResult.getBestStrike().getOptionPrice()) {
                    bestStrike = callResult.getBestStrike();
                } else {
                    bestStrike = putResult.getBestStrike();
                }
            } else if (callResult.isSuccess() && callResult.getBestStrike() != null) {
                bestStrike = callResult.getBestStrike();
            } else if (putResult.isSuccess() && putResult.getBestStrike() != null) {
                bestStrike = putResult.getBestStrike();
            }
            
            if (bestStrike != null) {
                // Use the calculated option price from OI analysis
                double entryPrice = bestStrike.getOptionPrice();
                
                // Ensure minimum price
                entryPrice = Math.max(entryPrice, 1.0);
                
                log.debug("OI-based entry LTP calculation - Index: {}, Strike: {}, Entry Price: {}, OI: {}",
                        currentIndexPrice, bestStrike.getStrikePrice(), entryPrice, bestStrike.getOi());
                
                return entryPrice;
            }
            
            return null;
            
        } catch (Exception e) {
            log.warn("Error in OI-based entry pricing calculation: {}", e.getMessage());
            return null;
        }
    }

}
