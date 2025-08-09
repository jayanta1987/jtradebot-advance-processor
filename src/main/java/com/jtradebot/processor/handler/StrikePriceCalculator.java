package com.jtradebot.processor.handler;

public class StrikePriceCalculator {

    private static final String STRIKE_TYPE = "ATM"; // "ITM", "ATM"
    private static final int STRIKE_DIFFERENCE = 100;

    public static String calculateStrikePrice(Double ltp, String instrumentType) {
        String strikePrice = getBaseStrikePrice(ltp, instrumentType);

        if (STRIKE_TYPE.equals("ITM")) {
            if (instrumentType.equals("PE")) {
                strikePrice = String.valueOf(Integer.parseInt(strikePrice) + STRIKE_DIFFERENCE);
            } else {
                strikePrice = String.valueOf(Integer.parseInt(strikePrice) - STRIKE_DIFFERENCE);
            }
        }

        return strikePrice;
    }


    private static String getBaseStrikePrice(Double ltp, String instrumentType) {
        if (instrumentType.equals("PE")) {
            return String.valueOf((int) Math.ceil(ltp / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE);
        } else {
            return String.valueOf((int) Math.floor(ltp / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE);
        }
    }

}