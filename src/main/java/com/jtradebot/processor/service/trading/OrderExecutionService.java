package com.jtradebot.processor.service.trading;


import com.jtradebot.processor.handler.KiteInstrumentHandler;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.JtradeOrder;
import com.jtradebot.processor.service.ExitStrategyService;
import com.jtradebot.processor.service.price.OptionPricingService;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderExecutionService {

    private final ExitStrategyService exitStrategyService;
    private final OptionPricingService optionPricingService;
    private final KiteInstrumentHandler kiteInstrumentHandler;
    private final TickDataManager tickDataManager;
    private final KiteConnect kiteConnect;
    private final Environment environment;


    /**
     * Update live P&L for active trades
     */
    public void updateLivePnL(Tick tick) throws KiteException {
        try {
            // Get active order from ExitStrategyService (global check)
            List<JtradeOrder> activeOrders = exitStrategyService.getActiveOrders();
            if (activeOrders.isEmpty()) {
                return;
            }
            
            JtradeOrder activeOrder = activeOrders.get(0); // Get the first active order
            
            double currentOptionPrice;
            double currentIndexPrice;
            
            // Check if we're in live profile and have real option instrument token
            if (isLiveProfile() && activeOrder.getInstrumentToken() != null && activeOrder.getInstrumentToken() > 0) {
                // Get real option LTP for live profile using Kite Connect API
                String instrumentToken = String.valueOf(activeOrder.getInstrumentToken());
                currentOptionPrice = kiteConnect.getLTP(new String[]{instrumentToken}).get(instrumentToken).lastPrice;
                
                // Get current Nifty index price for reference
                String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
                Tick niftyTick = tickDataManager.getLastTick(niftyToken);
                currentIndexPrice = niftyTick != null ? niftyTick.getLastTradedPrice() : activeOrder.getEntryIndexPrice();
                
                log.debug("🎯 USING REAL OPTION LTP FROM KITE API - Token: {}, Symbol: {}, LTP: {}, Index: {}", 
                        activeOrder.getInstrumentToken(), activeOrder.getTradingSymbol(), currentOptionPrice, currentIndexPrice);
            } else {
                // Use calculated option price for local profile or when no real instrument token
                String niftyToken = kiteInstrumentHandler.getNifty50Token().toString();
                Tick niftyTick = tickDataManager.getLastTick(niftyToken);
                if (niftyTick == null) {
                    log.warn("No Nifty index data available for P&L calculation");
                    return;
                }
                
                currentIndexPrice = niftyTick.getLastTradedPrice();
                double entryIndexPrice = activeOrder.getEntryIndexPrice();
                double entryOptionPrice = activeOrder.getEntryPrice();
                
                // Calculate current option price based on index movement
                currentOptionPrice = optionPricingService.calculateCurrentLTP(
                    entryOptionPrice, entryIndexPrice, currentIndexPrice, activeOrder.getOrderType());
                
                log.debug("📊 USING CALCULATED OPTION PRICE - Entry: {}, Current: {}, Index: {}", 
                        entryOptionPrice, currentOptionPrice, currentIndexPrice);
            }
            
            double entryOptionPrice = activeOrder.getEntryPrice();
            double points = currentOptionPrice - entryOptionPrice;
            double pnl = points * activeOrder.getQuantity();
            
            // Simplified live P&L log (only points and P&L)
            String orderTypeDisplay = OrderTypeEnum.CALL_BUY.equals(activeOrder.getOrderType()) ? "CALL" : "PUT";
            log.info("_________________________ 💰 LIVE P&L - {} | Points: {}, P&L: ₹{}, LTP: {} _________________________", 
                    orderTypeDisplay, String.format("%+.2f", points), String.format("%.2f", pnl), String.format("%.2f", currentOptionPrice));
            
        } catch (Exception e) {
            log.error("Error updating live P&L for tick: {}", tick.getInstrumentToken(), e);
        }
    }

    /**
     * Check if we're in live profile
     */
    private boolean isLiveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("live".equals(profile)) {
                return true;
            }
        }
        return false;
    }


}
