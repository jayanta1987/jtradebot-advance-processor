package com.jtradebot.processor.repository.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.ExitReasonEnum;
import lombok.Data;
import lombok.ToString;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
public class OrderInfo {
    // CP details removed - no longer used in new strategy
    private EntryReason entryReason;
    private ExitReasonEnum exitReason;

    private double upProbabilityScore;
    private double downProbabilityScore;
}
