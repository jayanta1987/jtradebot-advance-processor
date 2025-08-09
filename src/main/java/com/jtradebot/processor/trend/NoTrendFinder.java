package com.jtradebot.processor.trend;


import com.jtradebot.processor.manager.TickDataManager;
import com.jtradebot.processor.model.enums.CandleTimeFrameEnum;
import com.jtradebot.processor.repository.document.Ntp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
@RequiredArgsConstructor
public class NoTrendFinder {

    private final TickDataManager tickDataManager;

    public Ntp getNoTradeScore(CandleTimeFrameEnum[] candleTimeFrameEnums) {
        Ntp ntp = new Ntp();
        for (CandleTimeFrameEnum candleTimeFrameEnum : candleTimeFrameEnums) {
            double noTradeScore = tickDataManager.getIndexData(candleTimeFrameEnum).getNoTradeScores().getTotalScore();
            switch (candleTimeFrameEnum) {
                case THREE_MIN -> ntp.setNtp3Min(noTradeScore);
                case FIVE_MIN -> ntp.setNtp5Min(noTradeScore);
                case FIFTEEN_MIN -> ntp.setNtp15Min(noTradeScore);
            }
        }
        return ntp;
    }
}
