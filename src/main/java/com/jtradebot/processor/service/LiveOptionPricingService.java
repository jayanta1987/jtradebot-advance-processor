package com.jtradebot.processor.service;

import com.jtradebot.processor.handler.StrikePriceCalculator;
import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.repository.document.Instrument;
import com.zerodhatech.models.Tick;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Quote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LiveOptionPricingService {

    private final StrikePriceCalculator strikePriceCalculator;
    private final TickDataManager tickDataManager;
    private final Environment environment;
    private final KiteConnect kiteConnect;

    /**
     * Get live option pricing information for live profile
     */
    public Optional<LiveOptionPricingInfo> getLiveOptionPricing(String orderType) throws KiteException {
        try {
            // Check if we're in live profile
            if (!isLiveProfile()) {
                log.debug("Not in live profile, using placeholder pricing");
                return Optional.empty();
            }

            // Get current Nifty index price
            Tick niftyTick = tickDataManager.getLastTick("256265"); // Nifty 50 index token
            if (niftyTick == null) {
                log.warn("No Nifty index data available for live option pricing");
                return Optional.empty();
            }

            double niftyIndexPrice = niftyTick.getLastTradedPrice();
            String optionType = "CALL_BUY".equals(orderType) ? "CE" : "PE";

            // Find the ITM option instrument (next available expiry)
            log.info("🔍 SEARCHING FOR OPTION INSTRUMENT - Index Price: {}, Option Type: {}", niftyIndexPrice, optionType);
            Optional<Instrument> optionInstrument = strikePriceCalculator.findOptionInstrument(niftyIndexPrice, optionType);

            if (!optionInstrument.isPresent()) {
                log.warn("❌ NO OPTION INSTRUMENT FOUND - Strike: {}, Type: {}, Index: {}", 
                        strikePriceCalculator.getITMStrikePrice(niftyIndexPrice, optionType), optionType, niftyIndexPrice);
                return Optional.empty();
            }

            Instrument instrument = optionInstrument.get();
            
            // Get the actual option LTP using Kite Connect API
            log.info("🔍 FETCHING OPTION LTP FROM KITE API - Token: {}, Symbol: {}", instrument.getInstrumentToken(), instrument.getTradingSymbol());
            
            // Call Kite Connect API to get LTP for the option instrument
            String instrumentToken = String.valueOf(instrument.getInstrumentToken());
            double optionLTP = kiteConnect.getLTP(new String[]{instrumentToken}).get(instrumentToken).lastPrice;
            
            log.info("✅ FOUND OPTION LTP FROM KITE API - Token: {}, Symbol: {}, LTP: {}", 
                    instrument.getInstrumentToken(), instrument.getTradingSymbol(), optionLTP);
            int strikePrice = strikePriceCalculator.getITMStrikePrice(niftyIndexPrice, optionType);

            LiveOptionPricingInfo pricingInfo = LiveOptionPricingInfo.builder()
                    .optionInstrument(instrument)
                    .optionLTP(optionLTP)
                    .niftyIndexPrice(niftyIndexPrice)
                    .strikePrice(strikePrice)
                    .optionType(optionType)
                    .orderType(orderType)
                    .build();

            log.info("🎯 LIVE OPTION PRICING - Symbol: {}, LTP: {}, Strike: {}, Index: {}, Type: {}", 
                    instrument.getTradingSymbol(), optionLTP, strikePrice, niftyIndexPrice, optionType);

            return Optional.of(pricingInfo);

        } catch (Exception e) {
            log.error("Error getting live option pricing for order type: {}", orderType, e);
            return Optional.empty();
        }
    }

    /**
     * Calculate entry price for live option trading
     */
    public Optional<Double> calculateLiveEntryPrice(String orderType) throws KiteException {
        Optional<LiveOptionPricingInfo> pricingInfo = getLiveOptionPricing(orderType);
        return pricingInfo.map(LiveOptionPricingInfo::getOptionLTP);
    }

    /**
     * Calculate stop loss price for live option trading
     */
    public Optional<Double> calculateLiveStopLossPrice(String orderType, double stopLossPoints) throws KiteException {
        Optional<LiveOptionPricingInfo> pricingInfo = getLiveOptionPricing(orderType);
        return pricingInfo.map(info -> Math.max(0.0, info.getOptionLTP() - stopLossPoints));
    }

    /**
     * Calculate target price for live option trading
     */
    public Optional<Double> calculateLiveTargetPrice(String orderType, double targetPoints) throws KiteException {
        Optional<LiveOptionPricingInfo> pricingInfo = getLiveOptionPricing(orderType);
        return pricingInfo.map(info -> info.getOptionLTP() + targetPoints);
    }

    /**
     * Check if we're in live profile
     */
    private boolean isLiveProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        log.info("🔍 PROFILE CHECK - Active profiles: {}", String.join(", ", activeProfiles));
        for (String profile : activeProfiles) {
            if ("live".equals(profile)) {
                log.info("✅ LIVE PROFILE DETECTED");
                return true;
            }
        }
        log.info("❌ LIVE PROFILE NOT DETECTED");
        return false;
    }

    /**
     * Data class to hold live option pricing information
     */
    @lombok.Data
    @lombok.Builder
    public static class LiveOptionPricingInfo {
        private Instrument optionInstrument;
        private double optionLTP;
        private double niftyIndexPrice;
        private int strikePrice;
        private String optionType;
        private String orderType;
    }
}
