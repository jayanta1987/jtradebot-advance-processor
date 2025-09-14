package com.jtradebot.processor.repository.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

@Document(collection = "config_trading_scenarios")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TradingScenario {
    
    @MongoId
    private String id;
    
    @Indexed(unique = true)
    private String name;
    
    private String description;
    private String targetMode;
    
    // Requirements configuration
    private Double minQualityScore;
    private Boolean flatMarketFilter;
    private Double minEmaPer;
    private Double minFutureSignalPer;
    private Double minCandlestickPer;
    private Double minMomentumPer;
    private Integer maxNTP;
    
    // Metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Boolean active;
    
    // Version control
    private Integer version;
    private String comments;
}
