package com.jtradebot.processor.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class EmaCrossOverConfirmation {
    private boolean isEma5CrossedEma34;
    private boolean isEma5CrossedEma14;
    private boolean isCurrentCandleCrossingEma;
}
