package com.jtradebot.processor.service.price;

import com.jtradebot.processor.model.enums.OrderTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockOptionPricingService {


    public Double calculateCurrentLTP(Double entryPrice, Double entryIndexPrice, Double currentIndexPrice, OrderTypeEnum orderType) {
        if (entryPrice == null || entryIndexPrice == null || currentIndexPrice == null) {
            log.warn("Invalid parameters for LTP calculation: entryPrice={}, entryIndexPrice={}, currentIndexPrice={}",
                    entryPrice, entryIndexPrice, currentIndexPrice);
            return entryPrice; // Return entry price if any value is null
        }

        // Calculate index movement
        Double indexMovement = currentIndexPrice - entryIndexPrice;

        // For options, price movement is 1:1 with index movement
        double currentLTP;

        if (orderType == OrderTypeEnum.CALL_BUY) {
            // CALL: option price increases when index increases
            currentLTP = entryPrice + indexMovement;
        } else if (orderType == OrderTypeEnum.PUT_BUY) {
            // PUT: option price decreases when index increases
            currentLTP = entryPrice - indexMovement;
        } else {
            log.warn("Unsupported order type for LTP calculation: {}", orderType);
            return entryPrice;
        }

        // Ensure LTP doesn't go below 0 (minimum option price)
        currentLTP = Math.max(currentLTP, 0.0);

        log.debug("Calculated LTP: {} (Entry: {}, Index Movement: {}, Order Type: {}, Entry Index: {}, Current Index: {})",
                currentLTP, entryPrice, indexMovement, orderType, entryIndexPrice, currentIndexPrice);
        return currentLTP;
    }

    public double calculateEntryLTP(Double currentIndexPrice) {
        return currentIndexPrice * 0.005; // Changed from 0.01 (1%) to 0.005 (0.5%)
    }

}
