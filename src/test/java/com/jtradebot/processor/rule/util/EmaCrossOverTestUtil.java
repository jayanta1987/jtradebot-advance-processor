package com.jtradebot.processor.rule.util;

import com.jtradebot.processor.model.EmaCrossOverConfirmation;

public class EmaCrossOverTestUtil {

    public static EmaCrossOverConfirmation getValidEma34CrossOverConfirmation() {
        EmaCrossOverConfirmation emaCrossOverConfirmation = new EmaCrossOverConfirmation();
        emaCrossOverConfirmation.setEma5CrossedEma34(true);
        emaCrossOverConfirmation.setEma5CrossedEma14(false);
        emaCrossOverConfirmation.setCurrentCandleCrossingEma(true);
        return emaCrossOverConfirmation;
    }

    public static EmaCrossOverConfirmation getValidEma14CrossOverConfirmation() {
        EmaCrossOverConfirmation emaCrossOverConfirmation = new EmaCrossOverConfirmation();
        emaCrossOverConfirmation.setEma5CrossedEma34(false);
        emaCrossOverConfirmation.setEma5CrossedEma14(true);
        emaCrossOverConfirmation.setCurrentCandleCrossingEma(true);
        return emaCrossOverConfirmation;
    }

    public static EmaCrossOverConfirmation getInValidEmaCrossOverConfirmation() {
        EmaCrossOverConfirmation emaCrossOverConfirmation = new EmaCrossOverConfirmation();
        emaCrossOverConfirmation.setEma5CrossedEma34(false);
        emaCrossOverConfirmation.setEma5CrossedEma14(false);
        emaCrossOverConfirmation.setCurrentCandleCrossingEma(false);
        return emaCrossOverConfirmation;
    }
}
