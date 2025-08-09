package com.jtradebot.processor.order;

public class ManualEntryRejectedException extends RuntimeException {
    public ManualEntryRejectedException(String message) {
        super(message);
    }
} 