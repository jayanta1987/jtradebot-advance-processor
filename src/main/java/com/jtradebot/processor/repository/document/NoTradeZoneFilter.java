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

@Document(collection = "config_no_trade_zone_filters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NoTradeZoneFilter {
    
    @MongoId
    private String id;
    
    @Indexed(unique = true)
    private String filterName; // "candleHeight", "volumeSurge", "bodyRatio", etc.
    
    private Boolean enabled;
    private Double ntp;
    private String name;
    private String description;
    private Double threshold;
    private Double minThreshold;
    private Double maxThreshold;
    private Integer priority;
    private Double overboughtThreshold;
    private Double oversoldThreshold;
    private String timeframe;
    private Integer lookbackBars;
    private Integer maxConsecutiveCount;
    private Integer analysisWindow;
    
    // Trading hours fields
    private Integer startHour;
    private Integer startMinute;
    private Integer endHour;
    private Integer endMinute;
    
    // Support/Resistance fields
    private Integer supportResistanceBuffer;
    private Integer roundFigureBuffer;
    
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
