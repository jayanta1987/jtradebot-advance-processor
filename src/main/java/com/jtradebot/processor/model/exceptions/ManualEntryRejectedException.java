package com.jtradebot.processor.model.exceptions;

public class ManualEntryRejectedException extends RuntimeException {
    public ManualEntryRejectedException(String message) {
        super(message);
    }
} 