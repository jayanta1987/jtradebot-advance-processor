package com.jtradebot.processor.manager;

public class OptionsChargesCalculator {

    // Constants
    private static final double BROKERAGE = 20.0;
    private static final double STT_SELL_PERCENTAGE = 0.1 / 100;  // Updated STT rate
    private static final double TRANSACTION_CHARGES_PERCENTAGE = 0.053 / 100;
    private static final double GST_PERCENTAGE = 18.0 / 100;
    private static final double SEBI_CHARGES_PER_CRORE = 10.0 / 10000000;
    private static final double STAMP_DUTY_BUY_PERCENTAGE = 0.003 / 100;

    public static double calculateBuyCharges(int quantity, double entryPrice) {
        double buyTurnover = quantity * entryPrice;

        // Calculate individual charges
        double brokerage = BROKERAGE;  // Since brokerage is flat per order
        double transactionCharges = buyTurnover * TRANSACTION_CHARGES_PERCENTAGE;
        double sebiCharges = buyTurnover * SEBI_CHARGES_PER_CRORE;
        double stampDuty = buyTurnover * STAMP_DUTY_BUY_PERCENTAGE;
        double gst = GST_PERCENTAGE * (brokerage + transactionCharges);

        // Calculate total charges
        return brokerage + transactionCharges + gst + sebiCharges + stampDuty;
    }

    public static double calculateSellCharges(int quantity, double exitPrice) {
        double sellTurnover = quantity * exitPrice;

        // Calculate individual charges
        double brokerage = BROKERAGE;  // Since brokerage is flat per order
        double stt = sellTurnover * STT_SELL_PERCENTAGE;
        double transactionCharges = sellTurnover * TRANSACTION_CHARGES_PERCENTAGE;
        double sebiCharges = sellTurnover * SEBI_CHARGES_PER_CRORE;
        double gst = GST_PERCENTAGE * (brokerage + transactionCharges);

        // Calculate total charges
        return brokerage + stt + transactionCharges + gst + sebiCharges;
    }

}