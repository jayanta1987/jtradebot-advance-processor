package com.jtradebot.processor.trend;

import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.PutData;
import com.jtradebot.processor.model.PutScoresTF;
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
public class PutTrendPointFinder {

    private final TickDataManager tickDataManager;

    public PutData getFinalPutScore(CandleTimeFrameEnum[] candleTimeFrameEnums) {
        List<PutScoresTF> putScoresTFs = new ArrayList<>();
        List<String> avgCandleHeights = new ArrayList<>();

        double maxScore = Double.NEGATIVE_INFINITY;
        double minScore = Double.POSITIVE_INFINITY;

        double totalWeightedScore = 0.0;
        double totalTpFactor = 0.0;

        for (CandleTimeFrameEnum candleTimeFrameEnum : candleTimeFrameEnums) {
            PutScoresTF putScoresTF = tickDataManager.getIndexData(candleTimeFrameEnum).getPutScoresTF();
            putScoresTFs.add(putScoresTF);
            avgCandleHeights.add(candleTimeFrameEnum.name() + "_" + "TBC");

            double totalScore = putScoresTF.getTotalScore();

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

        return getPutData(maxScore, minScore, avgScore, putScoresTFs, avgCandleHeights);
    }

    private @NotNull PutData getPutData(double maxScore, double minScore, double avgScore,List<PutScoresTF> putScoresTFs, List<String> avgCandleHeights) {
        PutData putData = new PutData();

        boolean isValidPut = false;
        for (PutScoresTF putScoresTF : putScoresTFs) {
            if (putScoresTF.checkValidScore() && putScoresTF.checkValidCandleStick()) {
                isValidPut = true;
                break;
            }
        }
        putData.setValidPut(isValidPut);
        putData.setMaxScore(maxScore);
        putData.setMinScore(minScore);
        putData.setAvgScore(avgScore);
        putData.setFinalScore(avgScore);
        putData.setAvgCandleHeights(avgCandleHeights);
        putData.setSupportMap(tickDataManager.getAllSupportMap());
        putData.setResistanceMap(tickDataManager.getAllResistanceMap());
        putData.setPutScoresTFList(putScoresTFs);

        return putData;
    }
}