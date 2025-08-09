package com.jtradebot.processor.mapper;

import com.jtradebot.processor.model.CallEmaRsiScores;
import com.jtradebot.processor.model.IntraDayConfirmationScores;
import com.jtradebot.processor.model.PutEmaRsiScores;
import com.jtradebot.processor.model.enums.CrossTypeEnum;

public class IntraDayScoreMapper {
    public static IntraDayConfirmationScores mapToIntraDayConfirmationScores(CallEmaRsiScores callEmaRsiScores) {
        IntraDayConfirmationScores intraDayConfirmationScores = new IntraDayConfirmationScores();
        intraDayConfirmationScores.setEma5CrossedEma34(callEmaRsiScores.getEma5CrossOverEma34());
        intraDayConfirmationScores.setEma5CrossedEma14(callEmaRsiScores.getEma5CrossOverEma14());
        intraDayConfirmationScores.setEma5CrossedEma200(callEmaRsiScores.getEma5CrossOverEma200());
        intraDayConfirmationScores.setCrossedMultipleEmas(callEmaRsiScores.getCrossedUpMultipleEmas());
        intraDayConfirmationScores.setLtpCrossedEma5(callEmaRsiScores.getLtpCrossedUpEma5());
        intraDayConfirmationScores.setLtpCrossedEma9(callEmaRsiScores.getLtpCrossedUpEma9());
        intraDayConfirmationScores.setLtpCrossedEma14(callEmaRsiScores.getLtpCrossedUpEma14());
        intraDayConfirmationScores.calculateTotalScore();
        intraDayConfirmationScores.setCrossType(CrossTypeEnum.CROSS_UP);
        return intraDayConfirmationScores;
    }

    public static IntraDayConfirmationScores mapToIntraDayConfirmationScores(PutEmaRsiScores putEmaRsiScores) {
        IntraDayConfirmationScores intraDayConfirmationScores = new IntraDayConfirmationScores();
        intraDayConfirmationScores.setEma5CrossedEma34(putEmaRsiScores.getEma5CrossDownEma34());
        intraDayConfirmationScores.setEma5CrossedEma14(putEmaRsiScores.getEma5CrossDownEma14());
        intraDayConfirmationScores.setEma5CrossedEma200(putEmaRsiScores.getEma5CrossDownEma200());
        intraDayConfirmationScores.setCrossedMultipleEmas(putEmaRsiScores.getCrossedDownMultipleEmas());
        intraDayConfirmationScores.setLtpCrossedEma5(putEmaRsiScores.getLtpCrossedDownEma5());
        intraDayConfirmationScores.setLtpCrossedEma9(putEmaRsiScores.getLtpCrossedDownEma9());
        intraDayConfirmationScores.setLtpCrossedEma14(putEmaRsiScores.getLtpCrossedDownEma14());
        intraDayConfirmationScores.calculateTotalScore();
        intraDayConfirmationScores.setCrossType(CrossTypeEnum.CROSS_DOWN);
        return intraDayConfirmationScores;
    }
}
