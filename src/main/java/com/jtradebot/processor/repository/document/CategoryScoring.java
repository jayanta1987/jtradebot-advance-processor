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
import java.util.Map;

@Document(collection = "config_category_scoring")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryScoring {
    
    @MongoId
    private String id;
    
    @Indexed(unique = true)
    private String categoryType; // "callCategories" or "putCategories"
    
    @Indexed(unique = true)
    private String categoryName; // "ema", "futureAndVolume", "candlestick", "momentum"
    
    // Indicator scoring values
    private Map<String, Double> indicators;
    
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
