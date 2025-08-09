package com.jtradebot.processor.trend;

import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.CallData;
import com.jtradebot.processor.model.CallScoresTF;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CallTrendPointFinder {

    private final TickDataManager tickDataManager;

    public CallData getFinalCallScore(CandleTimeFrameEnum[] candleTimeFrameEnums) {
        List<CallScoresTF> callScoresTFs = new ArrayList<>();
        List<String> avgCandleHeights = new ArrayList<>();

        double maxScore = Double.NEGATIVE_INFINITY;
        double minScore = Double.POSITIVE_INFINITY;

        double totalWeightedScore = 0.0;
        double totalTpFactor = 0.0;

        for (CandleTimeFrameEnum candleTimeFrameEnum : candleTimeFrameEnums) {
            CallScoresTF callScoresTF = tickDataManager.getIndexData(candleTimeFrameEnum).getCallScoresTF();
            callScoresTFs.add(callScoresTF);
            avgCandleHeights.add(candleTimeFrameEnum.name() + "_" + "TBC");

            double totalScore = callScoresTF.getTotalScore();

            double tpFactor = candleTimeFrameEnum.getTpFactor();
            totalWeightedScore += totalScore * tpFactor;
            totalTpFactor += tpFactor;

            if (totalScore > maxScore) {
                maxScore = totalScore;
            }
            if (totalScore < minScore) {
                minScore = totalScore;
            }

        }

        double avgScore = totalWeightedScore / totalTpFactor;
        return getCallData(maxScore, minScore, avgScore, callScoresTFs, avgCandleHeights);
    }

    private @NotNull CallData getCallData(double maxScore, double minScore, double avgScore, List<CallScoresTF> callScoresTFs, List<String> avgCandleHeights) {
        CallData callData = new CallData();

        boolean isValidCall = false;
        for (CallScoresTF callScoresTF : callScoresTFs) {
            if (callScoresTF.checkValidScore() && callScoresTF.checkValidCandleStick()) {
                isValidCall = true;
                break;
            }
        }
        callData.setValidCall(isValidCall);
        callData.setMaxScore(maxScore);
        callData.setMinScore(minScore);
        callData.setAvgScore(avgScore);
        callData.setFinalScore(avgScore);
        callData.setAvgCandleHeights(avgCandleHeights);
        callData.setSupportMap(tickDataManager.getAllSupportMap());
        callData.setResistanceMap(tickDataManager.getAllResistanceMap());
        callData.setCallScoresTFList(callScoresTFs);
        return callData;
    }

}
