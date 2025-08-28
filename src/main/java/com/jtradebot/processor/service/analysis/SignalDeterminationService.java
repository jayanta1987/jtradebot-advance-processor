package com.jtradebot.processor.service.analysis;

import com.jtradebot.processor.model.strategy.ScalpingEntryDecision;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service responsible for determining entry signals based on entry decisions.
 * This service centralizes the signal determination logic to ensure consistency
 * between entry decisions and logging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignalDeterminationService {

    /**
     * Determines entry signals based on the entry decision.
     * 
     * @param entryDecision The entry decision containing scenario and market direction
     * @return SignalResult containing shouldCall, shouldPut, and dominance flags
     */
    public SignalResult determineSignals(ScalpingEntryDecision entryDecision) {
        try {
            boolean scenarioPassed = entryDecision.isShouldEntry();
            String marketDirection = entryDecision.getMarketDirection();

            // Determine dominance based on market direction
            boolean isCallDominant = "CALL".equals(marketDirection);
            boolean isPutDominant = "PUT".equals(marketDirection);

            // Determine signals based on scenario and market direction
            boolean shouldCall = scenarioPassed && isCallDominant;
            boolean shouldPut = scenarioPassed && isPutDominant;

            log.debug("🔍 SIGNAL DETERMINATION - Scenario: {}, MarketDirection: {}, ScenarioPassed: {}, CallDominant: {}, PutDominant: {}, ShouldCall: {}, ShouldPut: {}",
                    entryDecision.getScenarioName(), marketDirection, scenarioPassed, isCallDominant, isPutDominant, shouldCall, shouldPut);

            return new SignalResult(shouldCall, shouldPut, isCallDominant, isPutDominant);

        } catch (Exception e) {
            log.error("Error determining signals: {}", e.getMessage(), e);
            return new SignalResult(false, false, false, false);
        }
    }

    /**
     * Result class containing signal determination results.
     */
    @Getter
    public static class SignalResult {
        private final boolean shouldCall;
        private final boolean shouldPut;
        private final boolean isCallDominant;
        private final boolean isPutDominant;

        public SignalResult(boolean shouldCall, boolean shouldPut, boolean isCallDominant, boolean isPutDominant) {
            this.shouldCall = shouldCall;
            this.shouldPut = shouldPut;
            this.isCallDominant = isCallDominant;
            this.isPutDominant = isPutDominant;
        }
    }
}
