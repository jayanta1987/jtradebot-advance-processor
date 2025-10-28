package com.jtradebot.processor.service.price;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockOptionPricingService {

    private final GreeksAnalysisService greeksAnalysisService;


    public Double calculateCurrentLTP(Double entryPrice, Double entryIndexPrice, Double currentIndexPrice, OrderTypeEnum orderType) {
        if (entryPrice == null || entryIndexPrice == null || currentIndexPrice == null) {
            log.warn("Invalid parameters for LTP calculation: entryPrice={}, entryIndexPrice={}, currentIndexPrice={}",
                    entryPrice, entryIndexPrice, currentIndexPrice);
            return entryPrice; // Return entry price if any value is null
        }

        // Try to use Greeks-based pricing first
        try {
            Double greeksBasedPrice = calculateCurrentLTPWithGreeks(entryPrice, entryIndexPrice, currentIndexPrice, orderType);
            if (greeksBasedPrice != null) {
                // Use the simplified Greeks-based pricing
                log.debug("Using simplified Greeks-based pricing: {} (Entry: {}, Index Movement: {}, Order Type: {})",
                        greeksBasedPrice, entryPrice, currentIndexPrice - entryIndexPrice, orderType);
                return greeksBasedPrice;
            }
        } catch (Exception e) {
            log.warn("Greeks-based pricing failed, falling back to simple calculation: {}", e.getMessage());
        }

        // Fallback to simple index movement calculation
        return calculateCurrentLTPSimple(entryPrice, entryIndexPrice, currentIndexPrice, orderType);
    }

    /**
     * Calculate current LTP using Greeks-based pricing
     * This provides more accurate option pricing by considering Delta, Gamma, and other Greeks
     */
    private Double calculateCurrentLTPWithGreeks(Double entryPrice, Double entryIndexPrice, Double currentIndexPrice, OrderTypeEnum orderType) {
        try {
            String optionType = (orderType == OrderTypeEnum.CALL_BUY) ? "CE" : "PE";
            
            // Get current Greeks analysis to find the best strike
            GreeksAnalysisService.BestStrikeResult bestStrikeResult = greeksAnalysisService.getBestStrikeForScalping(optionType);
            
            if (!bestStrikeResult.isSuccess() || bestStrikeResult.getBestStrike() == null) {
                log.debug("No Greeks data available for option type: {}", optionType);
                return null;
            }

            GreeksAnalysisService.StrikeGreeksData bestStrike = bestStrikeResult.getBestStrike();
            return calculateLTPWithGreeksData(entryPrice, entryIndexPrice, currentIndexPrice, orderType, bestStrike);
            
        } catch (Exception e) {
            log.warn("Error in Greeks-based pricing calculation: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Calculate LTP using specific Greeks data
     * This method can be used when you have specific strike and Greeks information
     */
    public Double calculateLTPWithGreeksData(Double entryPrice, Double entryIndexPrice, Double currentIndexPrice, 
                                           OrderTypeEnum orderType, GreeksAnalysisService.StrikeGreeksData strikeData) {
        try {
            OptionGreeksCalculator.OptionGreeks greeks = strikeData.getGreeks();
            
            // Calculate index movement
            double indexMovement = currentIndexPrice - entryIndexPrice;
            
            // Simple approach: multiply index movement by absolute Delta value
            double deltaPriceChange = Math.abs(greeks.getDelta()) * indexMovement;
            
            // Apply the price change based on option type
            double newOptionPrice;
            if (orderType == OrderTypeEnum.CALL_BUY) {
                // CALL: option price increases when index increases
                newOptionPrice = entryPrice + deltaPriceChange;
            } else {
                // PUT: option price decreases when index increases
                newOptionPrice = entryPrice - deltaPriceChange;
            }
            
            // Ensure price doesn't go below 0
            newOptionPrice = Math.max(newOptionPrice, 0.0);
            
            log.debug("Simple Greeks pricing - Entry: {}, Index Movement: {}, Delta: {}, Price Change: {}, New Price: {}, Strike: {}",
                    entryPrice, String.format("%.2f", indexMovement), String.format("%.3f", greeks.getDelta()), 
                    String.format("%.2f", deltaPriceChange), String.format("%.2f", newOptionPrice), strikeData.getStrikePrice());
            
            return newOptionPrice;
            
        } catch (Exception e) {
            log.warn("Error in Greeks-based pricing calculation with specific data: {}", e.getMessage());
            return null;
        }
    }

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
        // Try to use Greeks-based entry pricing first
        try {
            Double greeksBasedEntryPrice = calculateEntryLTPWithGreeks(currentIndexPrice);
            if (greeksBasedEntryPrice != null) {
                log.debug("Using Greeks-based entry pricing: {} (Index: {})", greeksBasedEntryPrice, currentIndexPrice);
                return greeksBasedEntryPrice;
            }
        } catch (Exception e) {
            log.warn("Greeks-based entry pricing failed, falling back to simple calculation: {}", e.getMessage());
        }
        
        // Fallback to simple percentage-based calculation
        return currentIndexPrice * 0.005; // Changed from 0.01 (1%) to 0.005 (0.5%)
    }

    /**
     * Calculate entry LTP using Greeks-based pricing
     * This provides more realistic entry prices based on current market conditions
     */
    private Double calculateEntryLTPWithGreeks(Double currentIndexPrice) {
        try {
            // Get Greeks analysis for both CE and PE to find the best strike
            GreeksAnalysisService.BestStrikeResult callResult = greeksAnalysisService.getBestStrikeForScalping("CE");
            GreeksAnalysisService.BestStrikeResult putResult = greeksAnalysisService.getBestStrikeForScalping("PE");
            
            // Use the strike with better pricing (lower price for entry)
            GreeksAnalysisService.StrikeGreeksData bestStrike = null;
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
                // Use the calculated option price from Greeks analysis
                double entryPrice = bestStrike.getOptionPrice();
                
                // Ensure minimum price
                entryPrice = Math.max(entryPrice, 1.0);
                
                log.debug("Greeks-based entry LTP calculation - Index: {}, Strike: {}, Entry Price: {}, Delta: {}",
                        currentIndexPrice, bestStrike.getStrikePrice(), entryPrice, String.format("%.3f", bestStrike.getGreeks().getDelta()));
                
                return entryPrice;
            }
            
            return null;
            
        } catch (Exception e) {
            log.warn("Error in Greeks-based entry pricing calculation: {}", e.getMessage());
            return null;
        }
    }

}
