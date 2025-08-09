package com.jtradebot.processor.rule.util;

import com.jtradebot.processor.model.CpDetails;
import com.jtradebot.processor.model.enums.OrderTypeEnum;
import com.jtradebot.processor.repository.document.Ntp;

public class ScalpingCpMovementTestUtil {

    public static CpDetails getValidCpDetailsForCallBuy() {
        Ntp ntp = new Ntp();
        ntp.setNtp3Min(0.8);
        CpDetails cpDetails = new CpDetails();
        cpDetails.setCp(8.7);
        cpDetails.setShortAvgCp(7.9);
        cpDetails.setMidAvgCp(5.5);
        cpDetails.setNtp(ntp);
        cpDetails.setOneMinCp(7.7);
        cpDetails.setOneMinAvgCp(7.0);
        cpDetails.setLongTrendCp(6.5);
        cpDetails.setLongTrendAvgCp(6.0);
        cpDetails.setOrderType(OrderTypeEnum.CALL_BUY);
        cpDetails.calculateAllCpDeltas();
        cpDetails.calculateAllCpInSameDirection(OrderTypeEnum.CALL_BUY);
        return cpDetails;
    }

    public static CpDetails getInValidCpDetailsForCallBuy() {
        Ntp ntp = new Ntp();
        ntp.setNtp3Min(0.8);
        CpDetails cpDetails = new CpDetails();
        cpDetails.setCp(8.0);
        cpDetails.setShortAvgCp(7.9);
        cpDetails.setMidAvgCp(5.5);
        cpDetails.setNtp(ntp);
        cpDetails.setOneMinCp(7.7);
        cpDetails.setOneMinAvgCp(7.0);
        cpDetails.setLongTrendCp(6.5);
        cpDetails.setLongTrendAvgCp(6.0);
        cpDetails.setOrderType(OrderTypeEnum.CALL_BUY);
        cpDetails.calculateAllCpDeltas();
        cpDetails.calculateAllCpInSameDirection(OrderTypeEnum.CALL_BUY);
        return cpDetails;
    }

    public static CpDetails getValidCpDetailsForPutBuy() {
        Ntp ntp = new Ntp();
        ntp.setNtp3Min(0.8);
        CpDetails cpDetails = new CpDetails();
        cpDetails.setCp(-8.7);
        cpDetails.setShortAvgCp(-7.9);
        cpDetails.setMidAvgCp(-5.5);
        cpDetails.setNtp(ntp);
        cpDetails.setOneMinCp(-7.7);
        cpDetails.setOneMinAvgCp(-7.0);
        cpDetails.setLongTrendCp(-6.5);
        cpDetails.setLongTrendAvgCp(-6.0);
        cpDetails.setOrderType(OrderTypeEnum.PUT_BUY);
        cpDetails.calculateAllCpDeltas();
        cpDetails.calculateAllCpInSameDirection(OrderTypeEnum.PUT_BUY);
        return cpDetails;
    }

    public static CpDetails getInValidCpDetailsForPutBuy() {
        Ntp ntp = new Ntp();
        ntp.setNtp3Min(0.8);
        CpDetails cpDetails = new CpDetails();
        cpDetails.setCp(-8.0);
        cpDetails.setShortAvgCp(-7.9);
        cpDetails.setMidAvgCp(-5.5);
        cpDetails.setNtp(ntp);
        cpDetails.setOneMinCp(-7.7);
        cpDetails.setOneMinAvgCp(-7.0);
        cpDetails.setLongTrendCp(-6.5);
        cpDetails.setLongTrendAvgCp(-6.0);
        cpDetails.setOrderType(OrderTypeEnum.PUT_BUY);
        cpDetails.calculateAllCpDeltas();
        cpDetails.calculateAllCpInSameDirection(OrderTypeEnum.PUT_BUY);
        return cpDetails;
    }
}
